/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.inspections.GroovyImmutableAnnotationInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;

/**
 * User: Dmitry.Krasilschikov
 * Date: 27.04.2009
 */
public class ImmutableAnnotationProcessor extends NonCodeMembersContributor {
  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiScopeProcessor processor,
                                     PsiElement place,
                                     ResolveState state) {
    if (!(qualifierType instanceof PsiClassType)) return;

    PsiClass psiClass = ((PsiClassType)qualifierType).resolve();
    if (!(psiClass instanceof GrTypeDefinition) || psiClass.getName() == null) return;

    PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList == null || modifierList.findAnnotation(GroovyImmutableAnnotationInspection.IMMUTABLE) == null) return;

    final LightMethodBuilder fieldsConstructor = new LightMethodBuilder(psiClass);
    for (PsiField field : psiClass.getFields()) {
      fieldsConstructor.addParameter(field.getName(), field.getType());
    }
    if (!processor.execute(fieldsConstructor, ResolveState.initial())) return;

    final LightMethodBuilder defaultConstructor = new LightMethodBuilder(psiClass);
    processor.execute(defaultConstructor, ResolveState.initial());
  }
}
