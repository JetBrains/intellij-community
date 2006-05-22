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
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public class PointlessArithmeticExpressionInspection
        extends ExpressionInspection {

    private static final Set<String> arithmeticTokens =
            new HashSet<String>(4);

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

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message("pointless.boolean.expression.ignore.option"),
                this, "m_ignoreExpressionsContainingConstants");
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "pointless.arithmetic.expression.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.NUMERIC_GROUP_NAME;
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle
                .message("expression.can.be.replaced.problem.descriptor",
                        calculateReplacementExpression((PsiExpression) infos[0]));
    }

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
        } else if (tokenType.equals(JavaTokenType.LE) || tokenType.equals(JavaTokenType.GE)) {
            return "true";
        } else if (tokenType.equals(JavaTokenType.LT) || tokenType.equals(JavaTokenType.GT)) {
            return "true";
        } else {
            return "";
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new PointlessArithmeticVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new PointlessArithmeticFix();
    }

    private class PointlessArithmeticFix extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "constant.conditional.expression.simplify.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiExpression expression =
                    (PsiExpression) descriptor.getPsiElement();
            final String newExpression =
                    calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    private class PointlessArithmeticVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            //to avoid drilldown
        }

        public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if (!(expression.getROperand() != null)) {
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
            final PsiExpression rhs = expression.getROperand();
            final PsiExpression lhs = expression.getLOperand();
            if (rhs == null) {
                return;
            }
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
                isPointless = comparisonExpressionIsPointless(lhs, rhs, tokenType);
                if(isPointless)
                {
                    //System.out.println("pointless comparison!");
                }
            } else {
                isPointless = false;
            }
            if (!isPointless) {
                return;
            }
            final PsiType expressionType = expression.getType();
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
            return isOne(rhs);
        }

        private boolean comparisonExpressionIsPointless(PsiExpression lhs, PsiExpression rhs,
                                                        IElementType comparison) {
            if (PsiType.INT.equals(lhs.getType()) && PsiType.INT.equals(rhs.getType())) {
                return intComparisonIsPointless(lhs, rhs, comparison);
            } else if (PsiType.LONG.equals(lhs.getType()) && PsiType.LONG.equals(rhs.getType())) {
                return longComparisonIsPointless(lhs, rhs, comparison);
            }
            return false;
        }

        private boolean intComparisonIsPointless(PsiExpression lhs, PsiExpression rhs, IElementType comparison) {
            //System.out.println("comparison = " + comparison);
            if (isMaxInt(lhs) || isMinInt(rhs)) {
                return JavaTokenType.GE.equals(comparison) || JavaTokenType.LT.equals(comparison);
            }
            if (isMinInt(lhs) || isMaxInt(rhs)) {
                return JavaTokenType.LE.equals(comparison) || JavaTokenType.GT.equals(comparison);
            }
            return false;
        }

        private boolean longComparisonIsPointless(PsiExpression lhs, PsiExpression rhs, IElementType comparison) {
            if (isMaxLong(lhs) || isMinLong(rhs)) {
                return JavaTokenType.GE.equals(comparison) || JavaTokenType.LT.equals(comparison);
            }
            if (isMinLong(lhs) || isMaxLong(rhs)) {
                return JavaTokenType.LE.equals(comparison) || JavaTokenType.GT.equals(comparison);
            }
            return false;
        }
    }

    /**
     * @noinspection FloatingPointEquality
     */
    boolean isZero(PsiExpression expression) {
        if (m_ignoreExpressionsContainingConstants &&
                !(expression instanceof PsiLiteralExpression)) {
            return false;
        }
        final Double value = (Double)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.DOUBLE);
        return value != null && value == 0.0;
    }

    /**
     * @noinspection FloatingPointEquality
     */
    boolean isOne(PsiExpression expression) {
        if (m_ignoreExpressionsContainingConstants &&
                !(expression instanceof PsiLiteralExpression)) {
            return false;
        }
        final Double value = (Double)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.DOUBLE);
        return !(value == null || value != 1.0);
    }

    private static boolean isMinDouble(PsiExpression expression) {

        final Double value = (Double)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.DOUBLE);
        return value != null && value == Double.MIN_VALUE;
    }

    private static boolean isMaxDouble(PsiExpression expression) {
        final Double value = (Double)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.DOUBLE);
        return value != null && value == Double.MAX_VALUE;
    }

    private static boolean isMinFloat(PsiExpression expression) {
        final Float value = (Float)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.FLOAT);
        return value != null && value == Float.MIN_VALUE;
    }

    private static boolean isMaxFloat(PsiExpression expression) {
        final Float value = (Float)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.FLOAT);
        return value != null && value == Float.MAX_VALUE;
    }

    private static boolean isMinInt(PsiExpression expression) {
        //System.out.println("PointlessArithmeticExpressionInspection.isMinInt");
        final Integer value = (Integer)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.INT);
        //System.out.println(value);
        final boolean returnVal = value != null && value == Integer.MIN_VALUE;
        //System.out.println(returnVal);
        return returnVal;
    }

    private static boolean isMaxInt(PsiExpression expression) {
        //System.out.println("PointlessArithmeticExpressionInspection.isMaxInt");
        final Integer value = (Integer)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.INT);
        //System.out.println(value);
        final boolean returnVal = value != null && value == Integer.MAX_VALUE;
        //System.out.println(returnVal);
        return returnVal;
    }

    private static boolean isMinLong(PsiExpression expression) {
        final Long value = (Long)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.LONG);
        return value != null && value == Long.MIN_VALUE;
    }

    private static boolean isMaxLong(PsiExpression expression) {
        final Long value = (Long)
                ConstantExpressionUtil.computeCastTo(
                        expression, PsiType.LONG);
        return value != null && value == Long.MAX_VALUE;
    }
}