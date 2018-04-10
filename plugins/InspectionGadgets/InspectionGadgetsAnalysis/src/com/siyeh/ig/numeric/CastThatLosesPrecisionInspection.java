/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CastThatLosesPrecisionInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreIntegerCharCasts = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreOverflowingByteCasts = false;

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "NumericCastThatLosesPrecision";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "cast.that.loses.precision.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiType operandType = (PsiType)infos[0];
    boolean negativeOnly = (boolean)infos[1];
    return InspectionGadgetsBundle.message(negativeOnly ?
                                           "cast.that.loses.precision.negative.problem.descriptor" :
                                           "cast.that.loses.precision.problem.descriptor",
                                           operandType.getPresentableText());
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("cast.that.loses.precision.option"), "ignoreIntegerCharCasts");
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.overflowing.byte.casts.option"), "ignoreOverflowingByteCasts");
    return panel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CastThatLosesPrecisionVisitor();
  }

  private class CastThatLosesPrecisionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
      final PsiType castType = expression.getType();
      if (!ClassUtils.isPrimitiveNumericType(castType)) {
        return;
      }
      final PsiExpression operand = expression.getOperand();
      if (operand == null) {
        return;
      }
      final PsiType operandType = operand.getType();
      if (!ClassUtils.isPrimitiveNumericType(operandType) || !TypeUtils.isNarrowingConversion(operandType, castType)) {
        return;
      }
      if (ignoreIntegerCharCasts && PsiType.INT.equals(operandType) && PsiType.CHAR.equals(castType)) {
        return;
      }
      if (PsiType.LONG.equals(operandType) && PsiType.INT.equals(castType)) {
        final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
        if (MethodUtils.isHashCode(method)) {
          return;
        }
      }
      Object result = ExpressionUtils.computeConstantExpression(operand);
      if (result instanceof Character) {
        result = Integer.valueOf(((Character)result).charValue());
      }
      if (result instanceof Number) {
        final Number number = (Number)result;
        if (ignoreOverflowingByteCasts && PsiType.INT.equals(operandType) && PsiType.BYTE.equals(castType)) {
          final int i = number.intValue();
          if (i > Byte.MIN_VALUE && i <= 255) {
            return;
          }
        }
        if (valueIsContainableInType(number, castType)) {
          return;
        }
      }
      final PsiTypeElement castTypeElement = expression.getCastType();
      if (castTypeElement == null) {
        return;
      }
      LongRangeSet targetRange = LongRangeSet.fromType(castType);
      LongRangeSet lostRange = LongRangeSet.all();
      if (targetRange != null && LongRangeSet.fromType(operandType) != null) {
        LongRangeSet valueRange = getValueRange(operand);
        lostRange = valueRange.subtract(targetRange);
        if (lostRange.isEmpty()) return;
      }
      registerError(castTypeElement, operandType, lostRange.max() < 0);
    }

    private LongRangeSet getValueRange(@NotNull PsiExpression operand) {
      LongRangeSet fact = CommonDataflow.getExpressionFact(operand, DfaFactType.RANGE);
      return fact == null ? LongRangeSet.all() : fact;
    }

    private boolean valueIsContainableInType(Number value, PsiType type) {
      final long longValue = value.longValue();
      final double doubleValue = value.doubleValue();
      if (PsiType.BYTE.equals(type)) {
        return longValue >= (long)Byte.MIN_VALUE &&
               longValue <= (long)Byte.MAX_VALUE &&
               doubleValue >= (double)Byte.MIN_VALUE &&
               doubleValue <= (double)Byte.MAX_VALUE;
      }
      else if (PsiType.CHAR.equals(type)) {
        return longValue >= (long)Character.MIN_VALUE &&
               longValue <= (long)Character.MAX_VALUE &&
               doubleValue >= (double)Character.MIN_VALUE &&
               doubleValue <= (double)Character.MAX_VALUE;
      }
      else if (PsiType.SHORT.equals(type)) {
        return longValue >= (long)Short.MIN_VALUE &&
               longValue <= (long)Short.MAX_VALUE &&
               doubleValue >= (double)Short.MIN_VALUE &&
               doubleValue <= (double)Short.MAX_VALUE;
      }
      else if (PsiType.INT.equals(type)) {
        return longValue >= (long)Integer.MIN_VALUE &&
               longValue <= (long)Integer.MAX_VALUE &&
               doubleValue >= (double)Integer.MIN_VALUE &&
               doubleValue <= (double)Integer.MAX_VALUE;
      }
      else if (PsiType.LONG.equals(type)) {
        return doubleValue >= (double)Long.MIN_VALUE && doubleValue <= (double)Long.MAX_VALUE;
      }
      else if (PsiType.FLOAT.equals(type)) {
        return doubleValue == value.floatValue();
      }
      else if (PsiType.DOUBLE.equals(type)) {
        return true;
      }
      return false;
    }
  }
}