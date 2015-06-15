/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class FloatingPointEqualityInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("floating.point.equality.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("floating.point.equality.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FloatingPointEqualityComparisonVisitor();
  }

  private static class FloatingPointEqualityComparisonVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      if (!TypeUtils.hasFloatingPointType(lhs) && !TypeUtils.hasFloatingPointType(rhs)) {
        return;
      }
      if (isInfinityOrZero(lhs) || isInfinityOrZero(rhs)) {
        return;
      }
      registerError(expression);
    }

    private static boolean isInfinityOrZero(PsiExpression expression) {
      final Object value = ExpressionUtils.computeConstantExpression(expression);
      if (value instanceof Double) {
        final Double aDouble = (Double)value;
        final double v = aDouble.doubleValue();
        return Double.isInfinite(v) || v == 0.0;
      }
      else if (value instanceof Float) {
        final Float aFloat = (Float)value;
        final float v = aFloat.floatValue();
        return Float.isInfinite(v) || v == 0.0f;
      }
      else if (value instanceof Integer) {
        final Integer integer = (Integer)value;
        return integer.intValue() == 0;
      }
      else if (value instanceof Long) {
        final Long aLong = (Long)value;
        return aLong.longValue() == 0;
      }
      return false;
    }
  }
}