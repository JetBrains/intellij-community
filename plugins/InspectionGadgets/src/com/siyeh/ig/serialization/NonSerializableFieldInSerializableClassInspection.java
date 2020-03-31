/*
 * Copyright 2006-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.serialization;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.AddToIgnoreIfAnnotatedByListQuickFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NonSerializableFieldInSerializableClassInspection extends SerializableInspectionBase {
  @SuppressWarnings({"PublicField"})
  public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet();

  @Override
  protected JComponent @NotNull [] createAdditionalOptions() {
    return new JComponent[]{SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      ignorableAnnotations, InspectionGadgetsBundle.message("ignore.if.annotated.by"))};
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "non.serializable.field.in.serializable.class.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    final PsiField field = (PsiField)infos[0];
    return AddToIgnoreIfAnnotatedByListQuickFix.build(field, ignorableAnnotations);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonSerializableFieldInSerializableClassVisitor();
  }

  private class NonSerializableFieldInSerializableClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      if (field.hasModifierProperty(PsiModifier.TRANSIENT) || field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiClass aClass = field.getContainingClass();
      if (aClass == null) {
        return;
      }
      if (ignoreAnonymousInnerClasses && aClass instanceof PsiAnonymousClass) {
        return;
      }
      if (!SerializationUtils.isSerializable(aClass)) {
        return;
      }
      PsiType fieldType = field.getType();
      if (SerializationUtils.isProbablySerializable(fieldType)) {
        return;
      }
      PsiClass fieldClass = PsiUtil.resolveClassInClassTypeOnly(fieldType);
      if (fieldClass != null && isIgnoredSubclass(fieldClass)) {
        return;
      }
      if (SerializationUtils.hasWriteObject(aClass) || SerializationUtils.hasWriteReplace(aClass)) {
        return;
      }
      if (isIgnoredSubclass(aClass)) {
        return;
      }
      if (AnnotationUtil.isAnnotated(field, ignorableAnnotations, 0)) {
        return;
      }
      registerFieldError(field, field);
    }
  }
}