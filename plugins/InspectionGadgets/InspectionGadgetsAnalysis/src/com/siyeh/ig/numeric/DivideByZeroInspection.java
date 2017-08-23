/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

public class DivideByZeroInspection extends BaseInspection {
  private static final LongRangeSet ZERO_RANGE = LongRangeSet.point(0);

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "divzero";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("divide.by.zero.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("divide.by.zero.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DivisionByZeroVisitor();
  }

  private static class DivisionByZeroVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!JavaTokenType.DIV.equals(tokenType) && !JavaTokenType.PERC.equals(tokenType)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      for (int i = 1; i < operands.length; i++) {
        final PsiExpression operand = operands[i];
        if (isZero(operand)) {
          registerError(operand);
          return;
        }
      }
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final PsiExpression rhs = expression.getRExpression();
      if (rhs == null) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.DIVEQ) && !tokenType.equals(JavaTokenType.PERCEQ) || !isZero(rhs)) {
        return;
      }
      registerError(expression);
    }

    private static boolean isZero(PsiExpression expression) {
      final Object value = ConstantExpressionUtil.computeCastTo(expression, PsiType.DOUBLE);
      if (value instanceof Double) {
        final double constantValue = ((Double)value).doubleValue();
        if (constantValue == 0.0) return true;
      }
      LongRangeSet range = CommonDataflow.getExpressionFact(expression, DfaFactType.RANGE);
      return ZERO_RANGE.equals(range);
    }
  }
}