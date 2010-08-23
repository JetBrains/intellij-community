/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
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
package org.jetbrains.plugins.groovy.lang.resolve.noncode;

import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

/**
 * @author Maxim.Medvedev
 */
public class GrInheritConstructorContributor extends NonCodeMembersContributor {
  public static final String INHERIT_CONSTRUCTOR_NAME = "groovy.transform.InheritConstructors";

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType, PsiScopeProcessor processor, GroovyPsiElement place, ResolveState state) {
    if (!(qualifierType instanceof PsiClassType)) return;
    final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)qualifierType).resolveGenerics();
    final PsiClass psiClass = resolveResult.getElement();
    if (!(psiClass instanceof GrTypeDefinition) ||
        ((GrTypeDefinition)psiClass).isAnonymous() ||
        psiClass.isInterface() ||
        psiClass.isEnum()) {
      return;
    }

    if (!hasInheritConstructorsAnnotation(psiClass)) return;

    final PsiClass superClass = psiClass.getSuperClass();
    if (superClass == null) return;
    final PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
    final PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, psiClass, substitutor);
    final ResolveState currentState = state.put(PsiSubstitutor.KEY, superClassSubstitutor);

    final PsiMethod[] constructors = superClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      if (!processor.execute(constructor, currentState)) return;
    }
  }

  public static boolean hasInheritConstructorsAnnotation(PsiClass psiClass) {
    final PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList == null) return false;
    final PsiAnnotation[] annotations = modifierList.getAnnotations();
    boolean hasInheritConstructors = false;
    for (PsiAnnotation annotation : annotations) {
      if (INHERIT_CONSTRUCTOR_NAME.equals(annotation.getQualifiedName())) {
        hasInheritConstructors = true;
        break;
      }
    }
    return hasInheritConstructors;
  }
}
