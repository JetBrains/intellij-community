/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class EqualsBetweenInconvertibleTypesInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "equals.between.inconvertible.types.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiType comparedType = (PsiType)infos[0];
    final PsiType comparisonType = (PsiType)infos[1];
    return InspectionGadgetsBundle.message(
      "equals.between.inconvertible.types.problem.descriptor",
      comparedType.getPresentableText(),
      comparisonType.getPresentableText());
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsBetweenInconvertibleTypesVisitor();
  }

  private static class EqualsBetweenInconvertibleTypesVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final boolean staticEqualsCall;
      if (MethodCallUtils.isEqualsCall(expression)) {
        staticEqualsCall = false;
      }
      else {
        final String name = methodExpression.getReferenceName();
        if (!"equals".equals(name) && !"equal".equals(name)) {
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
        final String qualifiedName = aClass.getQualifiedName();
        if (!"java.util.Objects".equals(qualifiedName) && !"com.google.common.base.Objects".equals(qualifiedName)) {
          return;
        }
        staticEqualsCall = true;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final PsiExpression expression1;
      final PsiExpression expression2;
      if (staticEqualsCall) {
        if (arguments.length != 2) {
          return;
        }
        expression1 = arguments[0];
        expression2 = arguments[1];
      }
      else {
        if (arguments.length != 1) {
          return;
        }
        expression1 = arguments[0];
        expression2 = methodExpression.getQualifierExpression();
      }

      final PsiType comparisonType;
      if (expression2 == null) {
        final PsiClass aClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
        if (aClass == null) {
          return;
        }
        comparisonType = TypeUtils.getType(aClass);
      } else {
        comparisonType = expression2.getType();
      }
      if (comparisonType == null) {
        return;
      }
      final PsiType comparedType = expression1.getType();
      if (comparedType == null) {
        return;
      }
      if (TypeUtils.areConvertible(comparedType, comparisonType)) {
        return;
      }
      registerMethodCallError(expression, comparedType, comparisonType);
    }
  }
}