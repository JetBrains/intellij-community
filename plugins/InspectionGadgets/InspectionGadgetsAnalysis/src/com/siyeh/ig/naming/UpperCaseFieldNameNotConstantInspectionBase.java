/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.naming;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class UpperCaseFieldNameNotConstantInspectionBase extends BaseInspection {
  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "upper.case.field.name.not.constant.display.name");
  }

  @Override
  @NotNull
  public String getID() {
    return "NonConstantFieldWithUpperCaseName";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "upper.case.field.name.not.constant.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UpperCaseFieldNameNotConstantVisitor();
  }

  private static class UpperCaseFieldNameNotConstantVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      if (field.hasModifierProperty(PsiModifier.STATIC) &&
          field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      final String fieldName = field.getName();
      if (fieldName == null) {
        return;
      }
      if (!fieldName.equals(fieldName.toUpperCase())) {
        return;
      }
      registerFieldError(field);
    }
  }
}
