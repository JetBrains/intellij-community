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

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.EqualityToEqualsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public class StringEqualityInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.comparison.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.comparison.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ObjectEqualityVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new EqualityToEqualsFix();
  }

  private static class ObjectEqualityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      if (!ExpressionUtils.hasStringType(lhs)) {
        return;
      }
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null || !ExpressionUtils.hasStringType(rhs)) {
        return;
      }
      if (ExpressionUtils.isNullLiteral(lhs) || ExpressionUtils.isNullLiteral(rhs)) {
        return;
      }
      final PsiJavaToken sign = expression.getOperationSign();
      registerError(sign);
    }
  }
}