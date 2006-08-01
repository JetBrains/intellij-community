/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class CastThatLosesPrecisionInspection extends ExpressionInspection {

    public String getID() {
        return "NumericCastThatLosesPrecision";
    }

    public String getGroupDisplayName() {
        return GroupNames.NUMERIC_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiType operandType = (PsiType)infos[0];
        return InspectionGadgetsBundle.message(
                "cast.that.loses.precision.problem.descriptor",
                operandType.getPresentableText());
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CastThatLosesPrecisionVisitor();
    }

    private static class CastThatLosesPrecisionVisitor
            extends BaseInspectionVisitor {

        /**
         * @noinspection StaticCollection
         */
        private static final Map<PsiType, Integer> s_typePrecisions =
                new HashMap<PsiType, Integer>(7);

        static {
            s_typePrecisions.put(PsiType.BYTE, 1);
            s_typePrecisions.put(PsiType.CHAR, 2);
            s_typePrecisions.put(PsiType.SHORT, 2);
            s_typePrecisions.put(PsiType.INT, 3);
            s_typePrecisions.put(PsiType.LONG, 4);
            s_typePrecisions.put(PsiType.FLOAT, 5);
            s_typePrecisions.put(PsiType.DOUBLE, 6);
        }

        public void visitTypeCastExpression(
                @NotNull PsiTypeCastExpression expression) {
            final PsiType castType = expression.getType();
            if (!ClassUtils.isPrimitiveNumericType(castType)) {
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if (operand == null) {
                return;
            }
            final PsiType operandType = operand.getType();
            if (!ClassUtils.isPrimitiveNumericType(operandType)) {
                return;
            }
            if (hasLowerPrecision(operandType, castType)) {
                return;
            }
            final PsiManager manager = expression.getManager();
            final PsiConstantEvaluationHelper evaluationHelper =
                    manager.getConstantEvaluationHelper();
            Object result =
                    evaluationHelper.computeConstantExpression(operand);

            if (result instanceof Character) {
                result = new Integer(((Character)result).charValue());
            }

            if (result instanceof Number) {
                final Number number = (Number)result;
                if (valueIsContainableType(number, castType)) {
                    return;
                }
            }

            final PsiTypeElement castTypeElement = expression.getCastType();
            registerError(castTypeElement, operandType);
        }

        private static boolean hasLowerPrecision(PsiType operandType,
                                                 PsiType castType) {
            final Integer operandPrecision = s_typePrecisions.get(operandType);
            final Integer castPrecision = s_typePrecisions.get(castType);
            return operandPrecision <= castPrecision;
        }

        private static boolean valueIsContainableType(Number value, PsiType type) {
            final long longValue = value.longValue();
            final double doubleValue = value.doubleValue();
            if (PsiType.BYTE.equals(type)) {
                return longValue >= (long)Byte.MIN_VALUE &&
                        longValue <= (long)Byte.MAX_VALUE &&
                        doubleValue >= (double)Byte.MIN_VALUE &&
                        doubleValue <= (double)Byte.MAX_VALUE;
            } else if (PsiType.CHAR.equals(type)) {
                return longValue >= (long)Character.MIN_VALUE &&
                        longValue <= (long)Character.MAX_VALUE &&
                        doubleValue >= (double)Character.MIN_VALUE &&
                        doubleValue <= (double)Character.MAX_VALUE;
            } else if (PsiType.SHORT.equals(type)) {
                return longValue >= (long)Short.MIN_VALUE &&
                        longValue <= (long)Short.MAX_VALUE &&
                        doubleValue >= (double)Short.MIN_VALUE &&
                        doubleValue <= (double)Short.MAX_VALUE;
            } else if (PsiType.INT.equals(type)) {
                return longValue >= (long)Integer.MIN_VALUE &&
                        longValue <= (long)Integer.MAX_VALUE &&
                        doubleValue >= (double)Integer.MIN_VALUE &&
                        doubleValue <= (double)Integer.MAX_VALUE;
            } else if (PsiType.LONG.equals(type)) {
                return longValue >= Long.MIN_VALUE &&
                        longValue <= Long.MAX_VALUE &&
                        doubleValue >= (double)Long.MIN_VALUE &&
                        doubleValue <= (double)Long.MAX_VALUE;
            } else if (PsiType.FLOAT.equals(type)) {
                return doubleValue == value.floatValue();
            } else if (PsiType.DOUBLE.equals(type)) {
                return true;
            }
            return false;
        }
    }
}