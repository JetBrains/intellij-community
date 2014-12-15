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

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CastThatLosesPrecisionInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreIntegerCharCasts = false;

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
    return InspectionGadgetsBundle.message("cast.that.loses.precision.problem.descriptor", operandType.getPresentableText());
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("cast.that.loses.precision.option"),
                                          this, "ignoreIntegerCharCasts");
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
      if (ignoreIntegerCharCasts) {
        if (PsiType.INT.equals(operandType) && PsiType.CHAR.equals(castType)) {
          return;
        }
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
        if (valueIsContainableInType(number, castType)) {
          return;
        }
      }
      final PsiTypeElement castTypeElement = expression.getCastType();
      if (castTypeElement == null) {
        return;
      }
      registerError(castTypeElement, operandType);
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
        return longValue >= Long.MIN_VALUE &&
               longValue <= Long.MAX_VALUE &&
               doubleValue >= (double)Long.MIN_VALUE &&
               doubleValue <= (double)Long.MAX_VALUE;
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