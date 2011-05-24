/*
 * Copyright 2011 Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryExplicitNumericCastInspection extends BaseInspection {
    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return "Unnecessary explicit numeric cast";
    }

    @NotNull
    @Override
    protected String buildErrorString(Object... infos) {
        final PsiExpression expression = (PsiExpression) infos[0];
        return " '" + expression.getText() + "' unnecessarily cast to <code>#ref</code>";
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new UnnecessaryExplicitNumericCastFix();
    }
    
    private static class UnnecessaryExplicitNumericCastFix
            extends InspectionGadgetsFix {
        @NotNull
        @Override
        public String getName() {
            return "Remove cast";
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiTypeCastExpression)) {
                return;
            }
            final PsiTypeCastExpression typeCastExpression =
                    (PsiTypeCastExpression) parent;
            if (isPrimitiveNumericCastNecessary(typeCastExpression)) {
                return;
            }
            final PsiExpression operand = typeCastExpression.getOperand();
            if (operand == null) {
                typeCastExpression.delete();
            } else {
                typeCastExpression.replace(operand);
            }
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryExplicitNumericCastVisitor();
    }

    private static class UnnecessaryExplicitNumericCastVisitor
            extends BaseInspectionVisitor {

        @Override
        public void visitTypeCastExpression(PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);
            final PsiType castType = expression.getType();
            if (!ClassUtils.isPrimitiveNumericType(castType)) {
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if (operand == null) {
                return;
            }
            final PsiType operandType = operand.getType();
            if (operandType == null || operandType.equals(castType)) {
                return;
            }
            if (isPrimitiveNumericCastNecessary(expression)) {
                return;
            }
            final PsiTypeElement typeElement = expression.getCastType();
            if (typeElement != null) {
            registerError(typeElement, operand);}
        }
    }

    static boolean isPrimitiveNumericCastNecessary(
            PsiTypeCastExpression expression) {
        final PsiType type = expression.getType();
        if (type == null) {
            return true;
        }
        final PsiExpression operand = expression.getOperand();
        if (operand == null) {
            return true;
        }
        final PsiType operandType = operand.getType();
        final PsiElement parent = expression.getParent();
        if (parent instanceof PsiBinaryExpression) {
            if (PsiType.INT.equals(type)) {
                return PsiType.LONG.equals(operandType) ||
                        PsiType.FLOAT.equals(operandType) ||
                        PsiType.DOUBLE.equals(operandType);
            }
            if (PsiType.LONG.equals(type) || PsiType.FLOAT.equals(type) ||
                    PsiType.DOUBLE.equals(type)) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) parent;
                final PsiExpression lhs = binaryExpression.getLOperand();
                final PsiExpression rhs = binaryExpression.getROperand();
                if (rhs == null) {
                    return true;
                }
                if (expression == lhs) {
                    final PsiType rhsType = rhs.getType();
                    if (type.equals(rhsType)) {
                        return false;
                    }
                } else if (expression == rhs) {
                    final PsiType lhsType = lhs.getType();
                    if (type.equals(lhsType)) {
                        return false;
                    }
                } else {
                    assert false;
                }
            }
        } else if (parent instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) parent;
            final PsiType lhsType = assignmentExpression.getType();
            if (!type.equals(lhsType)) {
                return true;
            }
            return !isHandledByAssignmentConversion(lhsType, operand);
        } else if (parent instanceof PsiVariable) {
            final PsiVariable variable = (PsiVariable) parent;
            final PsiType lhsType = variable.getType();
            if (!type.equals(lhsType)) {
                return true;
            }
            return !isHandledByAssignmentConversion(lhsType, operand);
        }
        return true;
    }

    static boolean isHandledByAssignmentConversion(PsiType lhsType,
                                                   PsiExpression operand) {
        // JLS 5.2 Assignment Conversion
        final PsiType operandType = operand.getType();
        if (PsiType.DOUBLE.equals(lhsType)) {
            if (PsiType.FLOAT.equals(operandType) ||
                    PsiType.LONG.equals(operandType) ||
                    PsiType.INT.equals(operandType) ||
                    PsiType.CHAR.equals(operandType) ||
                    PsiType.SHORT.equals(operandType) ||
                    PsiType.BYTE.equals(operandType)) {
                // widening
                return true;
            }
        } else if (PsiType.FLOAT.equals(lhsType)) {
            if (PsiType.LONG.equals(operandType) ||
                    PsiType.INT.equals(operandType) ||
                    PsiType.CHAR.equals(operandType) ||
                    PsiType.SHORT.equals(operandType) ||
                    PsiType.BYTE.equals(operandType)) {
                // widening
                return true;
            }
        } else if (PsiType.LONG.equals(lhsType)) {
            if (PsiType.INT.equals(operandType) ||
                    PsiType.CHAR.equals(operandType) ||
                    PsiType.SHORT.equals(operandType) ||
                    PsiType.BYTE.equals(operandType)) {
                // widening
                return true;
            }
        } else if (PsiType.INT.equals(lhsType)) {
            if (PsiType.CHAR.equals(operandType) ||
                    PsiType.SHORT.equals(operandType) ||
                    PsiType.BYTE.equals(operandType)) {
                // widening
                return true;
            }
        } else if (PsiType.SHORT.equals(lhsType)) {
            if (PsiType.INT.equals(operandType)) {
                final Object constant =
                        ExpressionUtils.computeConstantExpression(operand);
                if (!(constant instanceof Integer)) {
                    return false;
                }
                final int i = ((Integer) constant).intValue();
                if (i >= Short.MIN_VALUE && i <= Short.MAX_VALUE) {
                    // narrowing
                    return true;
                }
            }
        } else if (PsiType.CHAR.equals(lhsType)) {
            if (PsiType.INT.equals(operandType)) {
                final Object constant =
                        ExpressionUtils.computeConstantExpression(operand);
                if (!(constant instanceof Integer)) {
                    return false;
                }
                final int i = ((Integer) constant).intValue();
                if (i >= Character.MIN_VALUE && i <= Character.MAX_VALUE) {
                    // narrowing
                    return true;
                }
            }
        } else if (PsiType.BYTE.equals(lhsType)) {
            if (PsiType.INT.equals(operandType)) {
                final Object constant =
                        ExpressionUtils.computeConstantExpression(operand);
                if (!(constant instanceof Integer)) {
                    return false;
                }
                final int i = ((Integer) constant).intValue();
                if (i >= Byte.MIN_VALUE && i <= Byte.MAX_VALUE) {
                    // narrowing
                    return true;
                }
            }
        }
        return false;
    }
}
