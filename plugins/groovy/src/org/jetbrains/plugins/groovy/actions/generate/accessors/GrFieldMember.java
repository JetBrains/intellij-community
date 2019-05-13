/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.actions.generate.accessors;

import com.intellij.codeInsight.generation.EncapsulatableClassMember;
import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.actions.generate.GroovyGenerationInfo;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

/**
 * @author Max Medvedev
 */
public class GrFieldMember extends PsiElementClassMember<PsiField> implements EncapsulatableClassMember {
  private static final int FIELD_OPTIONS = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER;

  protected GrFieldMember(@NotNull PsiField field) {
    super(field, PsiFormatUtil.formatVariable(field, FIELD_OPTIONS, PsiSubstitutor.EMPTY));
  }

  @Override
  @Nullable
  public GroovyGenerationInfo<GrMethod> generateGetter() {
    PsiField field = getElement();
    final GrMethod method = createMethodIfNotExists(field, GroovyPropertyUtils.generateGetterPrototype(field));
    return method != null ? new GroovyGenerationInfo<>(method) : null;
  }

  @Nullable
  private static GrMethod createMethodIfNotExists(final PsiField field, @Nullable final GrMethod template) {
    PsiMethod existing = field.getContainingClass().findMethodBySignature(template, false);
    return existing == null || existing instanceof GrAccessorMethod ? template : null;
  }

  @Override
  @Nullable
  public GroovyGenerationInfo<GrMethod> generateSetter() {
    PsiField field = getElement();
    if (field.hasModifierProperty(PsiModifier.FINAL)) {
      return null;
    }
    final GrMethod method = createMethodIfNotExists(field, GroovyPropertyUtils.generateSetterPrototype(field));
    return method == null ? null : new GroovyGenerationInfo<>(method);
  }
}
