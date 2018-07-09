/*
 * Copyright 2008-2018 Bas Leijdekkers
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
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class AmbiguousMethodCallInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("ambiguous.method.call.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiClass superClass = (PsiClass)infos[0];
    final PsiClass outerClass = (PsiClass)infos[1];
    return InspectionGadgetsBundle.message("ambiguous.method.call.problem.descriptor", superClass.getName(), outerClass.getName());
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new AmbiguousMethodCallFix();
  }

  private static class AmbiguousMethodCallFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("ambiguous.method.call.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)parent.getParent();
      final String newExpressionText = "super." + methodCallExpression.getText();
      PsiReplacementUtil.replaceExpression(methodCallExpression, newExpressionText);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AmbiguousMethodCallVisitor();
  }

  private static class AmbiguousMethodCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier != null) {
        return;
      }
      PsiClass containingClass = ClassUtils.getContainingClass(expression);
      if (containingClass == null) {
        return;
      }
      final PsiMethod targetMethod = expression.resolveMethod();
      if (targetMethod == null) {
        return;
      }
      final PsiClass methodClass = targetMethod.getContainingClass();
      if (methodClass == null || !containingClass.isInheritor(methodClass, true)) {
        return;
      }
      containingClass = ClassUtils.getContainingClass(containingClass);
      boolean staticAccess = false;
      while (containingClass != null) {
        staticAccess |= containingClass.hasModifierProperty(PsiModifier.STATIC);
        final PsiMethod[] methods = containingClass.findMethodsBySignature(targetMethod, false);
        if (methods.length > 0 && !methodClass.equals(containingClass)) {
          if (!staticAccess || Arrays.stream(methods).anyMatch(m -> m.hasModifierProperty(PsiModifier.STATIC))) {
            registerMethodCallError(expression, methodClass, containingClass);
            return;
          }
        }
        containingClass = ClassUtils.getContainingClass(containingClass);
      }
    }
  }
}