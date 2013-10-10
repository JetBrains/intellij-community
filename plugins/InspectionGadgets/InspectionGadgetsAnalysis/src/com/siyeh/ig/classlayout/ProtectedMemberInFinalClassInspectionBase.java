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
package com.siyeh.ig.classlayout;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

public class ProtectedMemberInFinalClassInspectionBase extends BaseInspection {
  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("protected.member.in.final.class.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("protected.member.in.final.class.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ProtectedMemberInFinalClassVisitor();
  }

  private static class ProtectedMemberInFinalClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!method.hasModifierProperty(PsiModifier.PROTECTED)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || !containingClass.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (MethodUtils.hasSuper(method)) {
        return;
      }
      registerModifierError(PsiModifier.PROTECTED, method, PsiModifier.PROTECTED);
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      if (!field.hasModifierProperty(PsiModifier.PROTECTED)) {
        return;
      }
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass == null || !containingClass.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      registerModifierError(PsiModifier.PROTECTED, field, PsiModifier.PROTECTED);
    }
  }
}
