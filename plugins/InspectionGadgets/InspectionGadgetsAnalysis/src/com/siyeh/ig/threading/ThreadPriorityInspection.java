/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ThreadPriorityInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "CallToThreadSetPriority";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("thread.priority.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "thread.priority.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThreadSetPriorityVisitor();
  }

  private static class ThreadSetPriorityVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression methodCallExpression) {
      super.visitMethodCallExpression(methodCallExpression);
      if (!isThreadSetPriority(methodCallExpression)) {
        return;
      }
      if (hasNormalPriorityArgument(methodCallExpression)) {
        return;
      }
      registerMethodCallError(methodCallExpression);
    }

    private static boolean isThreadSetPriority(
      @NotNull PsiMethodCallExpression methodCallExpression) {
      final PsiReferenceExpression methodExpression =
        methodCallExpression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      @NonNls final String setPriority = "setPriority";
      if (!setPriority.equals(methodName)) {
        return false;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final String className = aClass.getQualifiedName();
      return "java.lang.Thread".equals(className);
    }

    private static boolean hasNormalPriorityArgument(
      @NotNull PsiMethodCallExpression methodCallExpression) {
      final PsiExpressionList argumentList =
        methodCallExpression.getArgumentList();
      final PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length != 1) {
        return false;
      }
      final PsiExpression expression = expressions[0];
      if (!(expression instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)expression;
      final String referenceName = referenceExpression.getReferenceName();
      @NonNls final String normPriority = "NORM_PRIORITY";
      if (!normPriority.equals(referenceName)) {
        return false;
      }
      final PsiElement element = referenceExpression.resolve();
      if (!(element instanceof PsiField)) {
        return false;
      }
      final PsiField field = (PsiField)element;
      final PsiClass aClass = field.getContainingClass();
      final String className = aClass.getQualifiedName();
      return "java.lang.Thread".equals(className);
    }
  }
}