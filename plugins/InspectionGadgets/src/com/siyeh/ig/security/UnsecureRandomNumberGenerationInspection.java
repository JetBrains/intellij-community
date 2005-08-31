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
package com.siyeh.ig.security;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class UnsecureRandomNumberGenerationInspection extends ExpressionInspection {

  public String getGroupDisplayName() {
    return GroupNames.SECURITY_GROUP_NAME;
  }

  public String buildErrorString(PsiElement location) {
    @NonNls final String text = location.getText();
    if ("random".equals(text)) {
      return InspectionGadgetsBundle.message("unsecure.random.number.generation.problem.descriptor1");
    }
    else if ("Random".equals(text)) {
      return InspectionGadgetsBundle.message("unsecure.random.number.generation.problem.descriptor2");
    }
    else {
      return InspectionGadgetsBundle.message("unsecure.random.number.generation.problem.descriptor3");
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new InsecureRandomNumberGenerationVisitor();
  }

  private static class InsecureRandomNumberGenerationVisitor extends BaseInspectionVisitor {

    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!TypeUtils.expressionHasTypeOrSubtype("java.util.Random", expression)) {
        return;
      }
      final PsiJavaCodeReferenceElement reference = expression.getClassReference();
      if (reference == null) {
        return;
      }
      registerError(reference);
    }

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      if (methodExpression == null) {
        return;
      }
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"random".equals(methodName)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String className = containingClass.getQualifiedName();
      if (!"java.lang.Math".equals(className)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}
