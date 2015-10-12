/*
 * Copyright 2006-2015 Dave Griffith, Bas Leijdekkers
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

package com.siyeh.ig.security;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DesignForExtensionInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("design.for.extension.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "design.for.extension.problem.descriptor");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiMethod method = (PsiMethod)infos[0];
    if (MethodUtils.isOverridden(method)) {
      return null;
    }
    return new MakeMethodFinalFix(method.getName());
  }

  private static class MakeMethodFinalFix extends InspectionGadgetsFix {

    private final String myMethodName;

    public MakeMethodFinalFix(String methodName) {
      myMethodName = methodName;
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("make.method.final.fix.name", myMethodName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Make method 'final'";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement().getParent();
      if (!(element instanceof PsiMethod)) {
        return;
      }
      final PsiMethod method = (PsiMethod)element;
      final PsiModifierList modifierList = method.getModifierList();
      modifierList.setModifierProperty(PsiModifier.FINAL, true);
    }
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return !FileTypeUtils.isInServerPageFile(file); // IDEADEV-25538
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DesignForExtensionVisitor();
  }

  private static class DesignForExtensionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (method.isConstructor()) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.PRIVATE) ||
          method.hasModifierProperty(PsiModifier.FINAL) ||
          method.hasModifierProperty(PsiModifier.ABSTRACT) ||
          method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.isEnum() || containingClass.isInterface() || containingClass.isAnnotationType()) {
        return;
      }
      if (containingClass.hasModifierProperty(PsiModifier.FINAL)) {
        return;
      }
      if (containingClass instanceof PsiAnonymousClass) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (ControlFlowUtils.isEmptyCodeBlock(body)) {
        return;
      }
      registerMethodError(method, method);
    }
  }
}