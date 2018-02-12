/*
 * Copyright 2006-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ComparisonUtils;
import org.jetbrains.annotations.NotNull;

public class BadOddnessInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("bad.oddness.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "bad.oddness.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BadOddnessVisitor();
  }

  private static class BadOddnessVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(
      @NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (expression.getROperand() == null) {
        return;
      }
      if (!ComparisonUtils.isEqualityComparison(expression)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      final PsiExpression rhs = expression.getROperand();
      if (isModTwo(lhs) && hasValue(rhs, 1)) {
        registerError(expression, expression);
      }
      if (isModTwo(rhs) && hasValue(lhs, 1)) {
        registerError(expression, expression);
      }
    }

    private static boolean isModTwo(PsiExpression exp) {
      if (!(exp instanceof PsiBinaryExpression)) {
        return false;
      }
      final PsiBinaryExpression binary = (PsiBinaryExpression)exp;
      final IElementType tokenType = binary.getOperationTokenType();
      if (!JavaTokenType.PERC.equals(tokenType)) {
        return false;
      }
      final PsiExpression rhs = binary.getROperand();
      final PsiExpression lhs = binary.getLOperand();
      if (rhs == null) {
        return false;
      }
      return hasValue(rhs, 2) && canBeNegative(lhs);
    }

    private static boolean canBeNegative(PsiExpression lhs) {
      LongRangeSet range = CommonDataflow.getExpressionFact(lhs, DfaFactType.RANGE);
      return range == null || range.min() < 0;
    }

    private static boolean hasValue(PsiExpression expression, int testValue) {
      final Integer value = (Integer)ConstantExpressionUtil.computeCastTo(expression, PsiType.INT);
      return value != null && value.intValue() == testValue;
    }
  }
}