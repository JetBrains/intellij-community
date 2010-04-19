/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public class PointlessArithmeticExpressionInspection
        extends BaseInspection {

    private static final Set<String> arithmeticTokens =
            new HashSet<String>(9);

    static {
        arithmeticTokens.add("+");
        arithmeticTokens.add("-");
        arithmeticTokens.add("*");
        arithmeticTokens.add("/");
        arithmeticTokens.add("%");
        arithmeticTokens.add(">");
        arithmeticTokens.add("<");
        arithmeticTokens.add("<=");
        arithmeticTokens.add(">=");
    }

    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreExpressionsContainingConstants = false;

    @Override
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "pointless.boolean.expression.ignore.option"),
                this, "m_ignoreExpressionsContainingConstants");
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "pointless.arithmetic.expression.display.name");
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "expression.can.be.replaced.problem.descriptor",
                calculateReplacementExpression((PsiExpression) infos[0]));
    }

    @NonNls
    String calculateReplacementExpression(
            PsiExpression expression) {
        final PsiBinaryExpression exp = (PsiBinaryExpression) expression;
        final PsiJavaToken sign = exp.getOperationSign();
        final PsiExpression lhs = exp.getLOperand();
        final PsiExpression rhs = exp.getROperand();
        assert rhs != null;
        final IElementType tokenType = sign.getTokenType();
        if (tokenType.equals(JavaTokenType.PLUS)) {
            if (isZero(lhs)) {
                return rhs.getText();
            } else {
                return lhs.getText();
            }
        } else if (tokenType.equals(JavaTokenType.MINUS)) {
            return lhs.getText();
        } else if (tokenType.equals(JavaTokenType.ASTERISK)) {
            if (isOne(lhs)) {
                return rhs.getText();
            } else if (isOne(rhs)) {
                return lhs.getText();
            } else {
                return "0";
            }
        } else if (tokenType.equals(JavaTokenType.DIV)) {
            return lhs.getText();
        } else if (tokenType.equals(JavaTokenType.PERC)) {
            return "0";
        } else if (tokenType.equals(JavaTokenType.LE) ||
                tokenType.equals(JavaTokenType.GE)) {
            return "true";
        } else if (tokenType.equals(JavaTokenType.LT) ||
                tokenType.equals(JavaTokenType.GT)) {
            return "false";
        } else {
            return "";
        }
    }

    @Override
    public InspectionGadgetsFix buildFix(Object... infos) {
        return new PointlessArithmeticFix();
    }

    private class PointlessArithmeticFix extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "constant.conditional.expression.simplify.quickfix");
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiExpression expression =
                    (PsiExpression) descriptor.getPsiElement();
            final String newExpression =
                    calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new PointlessArithmeticVisitor();
    }

    private class PointlessArithmeticVisitor extends BaseInspectionVisitor {

        @Override public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            final PsiExpression rhs = expression.getROperand();
            if (rhs == null) {
                return;
            }
            final PsiType expressionType = expression.getType();
            if (PsiType.DOUBLE.equals(expressionType) ||
                    PsiType.FLOAT.equals(expressionType)) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final String signText = sign.getText();
            if (!arithmeticTokens.contains(signText)) {
                return;
            }
            if (TypeUtils.expressionHasType("java.lang.String", expression)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final IElementType tokenType = sign.getTokenType();
            final boolean isPointless;
            if (tokenType.equals(JavaTokenType.PLUS)) {
                isPointless = additionExpressionIsPointless(lhs, rhs);
            } else if (tokenType.equals(JavaTokenType.MINUS)) {
                isPointless = subtractionExpressionIsPointless(rhs);
            } else if (tokenType.equals(JavaTokenType.ASTERISK)) {
                isPointless = multiplyExpressionIsPointless(lhs, rhs);
            } else if (tokenType.equals(JavaTokenType.DIV)) {
                isPointless = divideExpressionIsPointless(rhs);
            } else if (tokenType.equals(JavaTokenType.PERC)) {
                isPointless = modExpressionIsPointless(rhs);
            } else if (tokenType.equals(JavaTokenType.LE) ||
                    tokenType.equals(JavaTokenType.GE) ||
                    tokenType.equals(JavaTokenType.GT) ||
                    tokenType.equals(JavaTokenType.LT)) {
                isPointless = comparisonExpressionIsPointless(lhs, rhs,
                        tokenType);
            } else {
                isPointless = false;
            }
            if (!isPointless) {
                return;
            }
            if (!PsiType.BOOLEAN.equals(expressionType)) {
                if (expressionType == null ||
                        !expressionType.equals(rhs.getType()) ||
                        !expressionType.equals(lhs.getType())) {
                    // A bit rude way to avoid false positive of
                    // 'int sum = 5, n = 6; float p = (1.0f * sum) / n;'
                    return;
                }
            }
            registerError(expression, expression);
        }

        private boolean subtractionExpressionIsPointless(PsiExpression rhs) {
            return isZero(rhs);
        }

        private boolean additionExpressionIsPointless(PsiExpression lhs,
                                                      PsiExpression rhs) {
            return isZero(lhs) || isZero(rhs);
        }

        private boolean multiplyExpressionIsPointless(PsiExpression lhs,
                                                      PsiExpression rhs) {
            return isZero(lhs) || isZero(rhs) || isOne(lhs) || isOne(rhs);
        }

        private boolean divideExpressionIsPointless(PsiExpression rhs) {
            return isOne(rhs);
        }

        private boolean modExpressionIsPointless(PsiExpression rhs) {
            return PsiType.INT.equals(rhs.getType()) && isOne(rhs);
        }

        private boolean comparisonExpressionIsPointless(
                PsiExpression lhs, PsiExpression rhs, IElementType comparison) {
            if (PsiType.INT.equals(lhs.getType()) &&
                PsiType.INT.equals(rhs.getType())) {
                return intComparisonIsPointless(lhs, rhs, comparison);
            } else if (PsiType.LONG.equals(lhs.getType()) &&
                       PsiType.LONG.equals(rhs.getType())) {
                return longComparisonIsPointless(lhs, rhs, comparison);
            }
            return false;
        }

        private boolean intComparisonIsPointless(
                PsiExpression lhs, PsiExpression rhs, IElementType comparison) {
            if (isMaxInt(lhs) || isMinInt(rhs)) {
                return JavaTokenType.GE.equals(comparison) ||
                       JavaTokenType.LT.equals(comparison);
            }
            if (isMinInt(lhs) || isMaxInt(rhs)) {
                return JavaTokenType.LE.equals(comparison) ||
                       JavaTokenType.GT.equals(comparison);
            }
            return false;
        }

        private boolean longComparisonIsPointless(
                PsiExpression lhs, PsiExpression rhs, IElementType comparison) {
            if (isMaxLong(lhs) || isMinLong(rhs)) {
                return JavaTokenType.GE.equals(comparison) ||
                       JavaTokenType.LT.equals(comparison);
            }
            if (isMinLong(lhs) || isMaxLong(rhs)) {
                return JavaTokenType.LE.equals(comparison) ||
                       JavaTokenType.GT.equals(comparison);
            }
            return false;
        }
    }

    boolean isZero(PsiExpression expression) {
        if (m_ignoreExpressionsContainingConstants &&
                !(expression instanceof PsiLiteralExpression)) {
            return false;
        }
        return ExpressionUtils.isZero(expression);
    }

    boolean isOne(PsiExpression expression) {
        if (m_ignoreExpressionsContainingConstants &&
                !(expression instanceof PsiLiteralExpression)) {
            return false;
        }
        return ExpressionUtils.isOne(expression);
    }

    private static boolean isMinDouble(PsiExpression expression) {
        final Double value = (Double)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.DOUBLE);
        return value != null && value.doubleValue() == Double.MIN_VALUE;
    }

    private static boolean isMaxDouble(PsiExpression expression) {
        final Double value = (Double)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.DOUBLE);
        //noinspection FloatingPointEquality
        return value != null && value.doubleValue() == Double.MAX_VALUE;
    }

    private static boolean isMinFloat(PsiExpression expression) {
        final Float value = (Float)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.FLOAT);
        //noinspection FloatingPointEquality
        return value != null && value.floatValue() == Float.MIN_VALUE;
    }

    private static boolean isMaxFloat(PsiExpression expression) {
        final Float value = (Float)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.FLOAT);
        //noinspection FloatingPointEquality
        return value != null && value.floatValue() == Float.MAX_VALUE;
    }

    private static boolean isMinInt(PsiExpression expression) {
        final Integer value = (Integer)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.INT);
        return value != null && value.intValue() == Integer.MIN_VALUE;
    }

    private static boolean isMaxInt(PsiExpression expression) {
        final Integer value = (Integer)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.INT);
        return value != null && value.intValue() == Integer.MAX_VALUE;
    }

    private static boolean isMinLong(PsiExpression expression) {
        final Long value = (Long)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.LONG);
        return value != null && value.longValue() == Long.MIN_VALUE;
    }

    private static boolean isMaxLong(PsiExpression expression) {
        final Long value = (Long)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.LONG);
        return value != null && value.longValue() == Long.MAX_VALUE;
    }
}