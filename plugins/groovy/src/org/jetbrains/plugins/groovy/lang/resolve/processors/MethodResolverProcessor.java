/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

/**
 * @author ven
 * Resolves methods from method call or function application.
 */
public class MethodResolverProcessor extends ResolverProcessor {
  @Nullable
  PsiType[] myArgumentTypes;

  private Set<GroovyResolveResult> myInapplicableCandidates = new LinkedHashSet<GroovyResolveResult>();
  private boolean myIsConstructor;

  public MethodResolverProcessor(String name, GroovyPsiElement place, boolean forCompletion, boolean isConstructor) {
    super(name, EnumSet.of(ResolveKind.METHOD, ResolveKind.PROPERTY), place, forCompletion);
    myIsConstructor = isConstructor;
    myArgumentTypes = PsiUtil.getArgumentTypes(place);
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      if (method.isConstructor() != myIsConstructor) return true; //not interested in constructors <now>

      if (!isAccessible((PsiNamedElement) element)) return true;

      if (myForCompletion || PsiUtil.isApplicable(myArgumentTypes, method)) {
        myCandidates.add(new GroovyResolveResultImpl(method, true));
      }
      else {
        myInapplicableCandidates.add(new GroovyResolveResultImpl(method, true));
      }

      return true;
    } else {
      return super.execute(element, substitutor);
    }
  }

  public GroovyResolveResult[] getCandidates() {
    if (myCandidates.size() > 0) {
      if (myForCompletion) {
        return myCandidates.toArray(new GroovyResolveResult[myInapplicableCandidates.size()]);
      }

      return filterCandidates();
    }
    return myInapplicableCandidates.toArray(new GroovyResolveResult[myInapplicableCandidates.size()]);
  }

  private GroovyResolveResult[] filterCandidates() {
    GroovyResolveResult[] array = myCandidates.toArray(new GroovyResolveResult[myCandidates.size()]);
    if (array.length == 1) return array;

    List<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();
    result.add(array[0]);

    PsiManager manager = myPlace.getManager();
    GlobalSearchScope scope = myPlace.getResolveScope();
    
    Outer:
    for (int i = 1; i < array.length; i++) {
      PsiElement currentElement = array[i].getElement();
      if (currentElement instanceof PsiMethod) {
        PsiMethod currentMethod = (PsiMethod) currentElement;
        for (Iterator<GroovyResolveResult> iterator = result.iterator(); iterator.hasNext();) {
          PsiElement element = iterator.next().getElement();
          if (element instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) element;
            if (dominated(currentMethod, method, manager, scope)) {
              continue Outer;
            } else if (dominated(method, currentMethod, manager, scope)) {
              iterator.remove();
            }
          }
        }
      }

      result.add(array[i]);
    }

    return result.toArray(new GroovyResolveResult[result.size()]);
  }

  private boolean dominated(PsiMethod method1, PsiMethod method2, PsiManager manager, GlobalSearchScope scope) {  //method1 has more general parameter types thn method2
    PsiParameter[] params1 = method1.getParameterList().getParameters();
    PsiParameter[] params2 = method2.getParameterList().getParameters();

    if (params1.length != params2.length) return false;

    for (int i = 0; i < params1.length; i++) {
      PsiType type1 = params1[i].getType();
      PsiType type2 = params2[i].getType();
      if (!TypesUtil.isAssignable(type1, type2, manager, scope)) return false;
    }

    return true;
  }


  public boolean hasCandidates() {
    return super.hasCandidates() || myInapplicableCandidates.size() > 0;
  }
}
