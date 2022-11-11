/*
 * Copyright 2007 Bas Leijdekkers
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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.InitializationUtils;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

public class TransientFieldNotInitializedInspection extends BaseInspection {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "transient.field.not.initialized.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReadObjectInitializationVisitor();
  }

  private static class ReadObjectInitializationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      if (field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.TRANSIENT)) {
        return;
      }
      final PsiClass containingClass = field.getContainingClass();
      if (!SerializationUtils.isSerializable(containingClass)) {
        return;
      }
      final PsiExpression initializer = field.getInitializer();
      if (initializer == null &&
          !InitializationUtils.isInitializedInInitializer(field, containingClass) &&
          !InitializationUtils.isInitializedInConstructors(field, containingClass)) {
        return;
      }
      if (SerializationUtils.hasReadObject(containingClass)) {
        return;
      }
      registerFieldError(field);
    }
  }
}