/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class SubstringZeroInspection extends ExpressionInspection {

  private final SubstringZeroVisitor fix = new SubstringZeroVisitor();

  public String getGroupDisplayName() {
    return GroupNames.PERFORMANCE_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new StringToStringVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class SubstringZeroVisitor extends InspectionGadgetsFix {
    public String getName() {
      return "Simplify";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiMethodCallExpression call = (PsiMethodCallExpression)descriptor
        .getPsiElement();
      final PsiReferenceExpression expression = call
        .getMethodExpression();
      final PsiExpression qualifier = expression.getQualifierExpression();
      final String qualifierText = qualifier.getText();
      replaceExpression(call, qualifierText);
    }
  }

  private static class StringToStringVisitor extends BaseInspectionVisitor {
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression
        .getMethodExpression();
      if (methodExpression == null) {
        return;
      }
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"substring".equals(methodName)) {
        return;
      }

      final PsiExpressionList argList = expression.getArgumentList();
      if (argList == null) {
        return;
      }
      final PsiExpression[] args = argList.getExpressions();
      if (args.length != 1) {
        return;
      }
      final PsiExpression arg = args[0];
      if (arg == null) {
        return;
      }
      if (!isZero(arg)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String className = aClass.getQualifiedName();
      if (!"java.lang.String".equals(className)) {
        return;
      }
      registerError(expression);
    }

    private static boolean isZero(PsiExpression expression) {
      final Integer value = (Integer)ConstantExpressionUtil
        .computeCastTo(expression, PsiType.INT);
      return value != null && value == 0;
    }
  }
}
