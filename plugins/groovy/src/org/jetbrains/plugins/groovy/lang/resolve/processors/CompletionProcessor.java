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
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/**
 * @author ven
 */
public class CompletionProcessor extends ResolverProcessor {

  private CompletionProcessor(PsiElement place, final EnumSet<ResolveKind> resolveTargets, final String name) {
    super(name, resolveTargets, place, PsiType.EMPTY_ARRAY);
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
    super.execute(element, substitutor);
    return true;
  }

  public static CompletionProcessor createPropertyCompletionProcessor(PsiElement place) {
    return new CompletionProcessor(place, EnumSet.of(ResolveKind.METHOD, ResolveKind.PROPERTY), null);
  }

  public static CompletionProcessor createRefSameNameProcessor(PsiElement place, String name) {
    return new CompletionProcessor(place, EnumSet.of(ResolveKind.METHOD, ResolveKind.PROPERTY), name);
  }

  public static CompletionProcessor createClassCompletionProcessor(PsiElement place) {
    return new CompletionProcessor(place, EnumSet.of(ResolveKind.CLASS_OR_PACKAGE), null);
  }

  private GroovyResolveResult[] filterCandidates() {
    GroovyResolveResult[] array = myCandidates.toArray(new GroovyResolveResult[myCandidates.size()]);
    if (array.length == 1) return array;

    List<GroovyResolveResult> result = new ArrayList<GroovyResolveResult>();
    result.add(array[0]);

    Outer:
    for (int i = 1; i < array.length; i++) {
      PsiElement currentElement = array[i].getElement();
      if (currentElement instanceof PsiMethod) {
        PsiMethod currentMethod = (PsiMethod) currentElement;
        for (Iterator<GroovyResolveResult> iterator = result.iterator(); iterator.hasNext();) {
          final GroovyResolveResult otherResolveResult = iterator.next();
          PsiElement element = otherResolveResult.getElement();
          if (element instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) element;
            if (dominated(currentMethod, array[i].getSubstitutor(), method, otherResolveResult.getSubstitutor())) {
              continue Outer;
            } else
            if (dominated(method, otherResolveResult.getSubstitutor(), currentMethod, array[i].getSubstitutor())) {
              iterator.remove();
            }
          }
        }
      }

      result.add(array[i]);
    }

    return result.toArray(new GroovyResolveResult[result.size()]);
  }

  private boolean dominated(PsiMethod method1, PsiSubstitutor substitutor1, PsiMethod method2, PsiSubstitutor substitutor2) {  //method1 has more general parameter types thn method2
    if (!method1.getName().equals(method2.getName())) return false;

    PsiParameter[] params1 = method1.getParameterList().getParameters();
    PsiParameter[] params2 = method2.getParameterList().getParameters();

    if (params1.length < params2.length) {
      if (params1.length == 0) return false;
      final PsiType lastType = params1[params1.length - 1].getType(); //varargs applicability
      return lastType instanceof PsiArrayType;
    }

    for (int i = 0; i < params2.length; i++) {
      PsiType type1 = substitutor1.substitute(params1[i].getType());
      PsiType type2 = substitutor2.substitute(params2[i].getType());
      if (!type1.equals(type2)) return false;
    }

    return true;
  }

  public GroovyResolveResult[] getCandidates() {
    if (myCandidates.size() == 0) return GroovyResolveResult.EMPTY_ARRAY;
    return filterCandidates();
  }
}