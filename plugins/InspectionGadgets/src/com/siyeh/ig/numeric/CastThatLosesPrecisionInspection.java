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
package com.siyeh.ig.numeric;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class CastThatLosesPrecisionInspection extends ExpressionInspection {

  /**
   * @noinspection StaticCollection
   */
  private static final Map<PsiType, Integer> s_typePrecisions = new HashMap<PsiType, Integer>(7);

  static {
    s_typePrecisions.put(PsiType.BYTE, 1);
    s_typePrecisions.put(PsiType.CHAR, 2);
    s_typePrecisions.put(PsiType.SHORT, 2);
    s_typePrecisions.put(PsiType.INT, 3);
    s_typePrecisions.put(PsiType.LONG, 4);
    s_typePrecisions.put(PsiType.FLOAT, 5);
    s_typePrecisions.put(PsiType.DOUBLE, 6);
  }

  public String getID() {
    return "NumericCastThatLosesPrecision";
  }

  public String getGroupDisplayName() {
    return GroupNames.NUMERIC_GROUP_NAME;
  }

  public String buildErrorString(PsiElement location) {
    final PsiTypeCastExpression expression = (PsiTypeCastExpression)location.getParent();
    assert expression != null;
    final PsiExpression operand = expression.getOperand();
    final PsiType operandType = operand.getType();
    return InspectionGadgetsBundle.message("cast.that.loses.precision.problem.descriptor", operandType.getPresentableText());
  }

  public BaseInspectionVisitor buildVisitor() {
    return new CastThatLosesPrecisionVisitor();
  }

  private static class CastThatLosesPrecisionVisitor extends BaseInspectionVisitor {

    public void visitTypeCastExpression(@NotNull PsiTypeCastExpression exp) {
      final PsiType castType = exp.getType();
      if (!ClassUtils.isPrimitiveNumericType(castType)) {
        return;
      }

      final PsiExpression operand = exp.getOperand();

      final PsiType operandType = operand.getType();
      if (!ClassUtils.isPrimitiveNumericType(operandType)) {
        return;
      }

      if (hasLowerPrecision(operandType, castType)) {
        return;
      }
      final PsiTypeElement castTypeElement = exp.getCastType();
      registerError(castTypeElement);
    }

    private static boolean hasLowerPrecision(PsiType operandType, PsiType castType) {
      final Integer operandPrecision = s_typePrecisions.get(operandType);
      final Integer castPrecision = s_typePrecisions.get(castType);
      return (operandPrecision) <= (castPrecision);
    }

  }
}
