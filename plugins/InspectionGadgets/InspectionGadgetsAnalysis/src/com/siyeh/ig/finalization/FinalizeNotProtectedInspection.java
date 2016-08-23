/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.finalization;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

public class FinalizeNotProtectedInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "finalize.not.declared.protected.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "finalize.not.declared.protected.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FinalizeDeclaredProtectedVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ProtectedFinalizeFix();
  }

  private static class ProtectedFinalizeFix extends InspectionGadgetsFix {
     @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("make.protected.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement methodName = descriptor.getPsiElement();
      final PsiMethod method = (PsiMethod)methodName.getParent();
      assert method != null;
      final PsiModifierList modifiers = method.getModifierList();
      modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
      modifiers.setModifierProperty(PsiModifier.PRIVATE, false);
      modifiers.setModifierProperty(PsiModifier.PROTECTED, true);
    }
  }

  private static class FinalizeDeclaredProtectedVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!MethodUtils.isFinalize(method)) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.PROTECTED)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null || aClass.isInterface()) {
        return;
      }
      registerMethodError(method);
    }
  }
}