/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.siyeh.ig.classlayout;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class NonFinalFieldInEnumInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("non.final.field.in.enum.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiClass enumClass = (PsiClass)infos[0];
    return InspectionGadgetsBundle.message("non.final.field.in.enum.problem.descriptor", enumClass.getName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonFinalFieldInEnumVisitor();
  }

  private static class NonFinalFieldInEnumVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(PsiField field) {
      super.visitField(field);
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass == null || !containingClass.isEnum()) {
        return;
      }
      if (field.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      registerFieldError(field, containingClass);
    }
  }
}
