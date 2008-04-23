/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class ImplicitNumericConversionInspection extends BaseInspection {

    /** @noinspection StaticCollection */
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

    /**
     * @noinspection PublicField
     */
    public boolean m_ignoreWideningConversions = false;

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "implicit.numeric.conversion.display.name");
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "implicit.numeric.conversion.ignore.option"),
                this, "m_ignoreWideningConversions");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiType type = (PsiType) infos[1];
        final PsiType expectedType = (PsiType)infos[2];
        return InspectionGadgetsBundle.message(
                "implicit.numeric.conversion.problem.descriptor",
                type.getPresentableText(), expectedType.getPresentableText());
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ImplicitNumericConversionVisitor();
    }

    public InspectionGadgetsFix buildFix(Object... infos) {
        return new ImplicitNumericConversionFix((PsiExpression)infos[0],
                (PsiType) infos[2]);
    }

    private static class ImplicitNumericConversionFix
            extends InspectionGadgetsFix {

        private final String m_name;

         ImplicitNumericConversionFix(PsiExpression expression,
                                      PsiType expectedType) {
            if (isConvertible(expression, expectedType)) {
                m_name = InspectionGadgetsBundle.message(
                        "implicit.numeric.conversion.convert.quickfix",
                        expectedType.getCanonicalText());
            }
            else {
                m_name = InspectionGadgetsBundle.message(
                        "implicit.numeric.conversion.make.explicit.quickfix");
            }
        }

        @NotNull
        public String getName() {
            return m_name;
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiExpression expression = (
                    PsiExpression)descriptor.getPsiElement();
            final PsiType expectedType =
                    ExpectedTypeUtils.findExpectedType(expression, true);
            assert expectedType != null;
            if (isConvertible(expression, expectedType)) {
                final String newExpression =
                        convertExpression(expression, expectedType);
                if (newExpression == null) {
                    return;
                }
                replaceExpression(expression, newExpression);
            } else {
                final String newExpression;
                if (ParenthesesUtils.getPrecedence(expression) <=
                        ParenthesesUtils.TYPE_CAST_PRECEDENCE) {
                    newExpression = '(' + expectedType.getPresentableText() +
                            ')' + expression.getText();
                }
                else {
                    newExpression = '(' + expectedType.getPresentableText() +
                            ")(" + expression.getText() + ')';
                }
                replaceExpression(expression, newExpression);
            }
        }

        @Nullable
        @NonNls private static String convertExpression(
                PsiExpression expression, PsiType expectedType) {
            final PsiType expressionType = expression.getType();
            if (expressionType == null) {
                return null;
            }
            if (expressionType.equals(PsiType.INT) &&
                    expectedType.equals(PsiType.LONG)) {
                return expression.getText() + 'L';
            }
            if (expressionType.equals(PsiType.INT) &&
                    expectedType.equals(PsiType.FLOAT)) {
                return expression.getText() + ".0F";
            }
            if (expressionType.equals(PsiType.INT) &&
                    expectedType.equals(PsiType.DOUBLE)) {
                return expression.getText() + ".0";
            }
            if (expressionType.equals(PsiType.LONG) &&
                    expectedType.equals(PsiType.FLOAT)) {
                final String text = expression.getText();
                final int length = text.length();
                return text.substring(0, length - 1) + ".0F";
            }
            if (expressionType.equals(PsiType.LONG) &&
                    expectedType.equals(PsiType.DOUBLE)) {
                final String text = expression.getText();
                final int length = text.length();
                return text.substring(0, length - 1) + ".0";
            }
            if (expressionType.equals(PsiType.DOUBLE) &&
                    expectedType.equals(PsiType.FLOAT)) {
                final String text = expression.getText();
                final int length = text.length();
                if (text.charAt(length - 1) == 'd' ||
                        text.charAt(length - 1) == 'D') {
                    return text.substring(0, length - 1) + 'F';
                }
                else {
                    return text + 'F';
                }
            }
            if (expressionType.equals(PsiType.FLOAT) &&
                    expectedType.equals(PsiType.DOUBLE)) {
                final String text = expression.getText();
                final int length = text.length();
                return text.substring(0, length - 1);
            }
            return null;   //can't happen
        }

        private static boolean isConvertible(PsiExpression expression,
                                             PsiType expectedType) {
            if (!(expression instanceof PsiLiteralExpression) &&
                    !isNegatedLiteral(expression)) {
                return false;
            }
            final PsiType expressionType = expression.getType();
            if (expressionType == null) {
                return false;
            }
            if (hasLowerPrecision(expectedType, expressionType)) {
                return false;
            }
            if (isIntegral(expressionType) && isIntegral(expectedType)) {
                return true;
            }
            if (isIntegral(expressionType) && isFloatingPoint(expectedType)) {
                return true;
            }
            return isFloatingPoint(expressionType) &&
                    isFloatingPoint(expectedType);

        }

        private static boolean isNegatedLiteral(PsiExpression expression) {
            if (!(expression instanceof PsiPrefixExpression)) {
                return false;
            }
            final PsiPrefixExpression prefixExpression =
                    (PsiPrefixExpression)expression;
            final PsiJavaToken sign = prefixExpression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!JavaTokenType.MINUS.equals(tokenType)) {
                return false;
            }
            final PsiExpression operand = prefixExpression.getOperand();
            return operand instanceof PsiLiteralExpression;
        }

        private static boolean isIntegral(PsiType expressionType) {
            return expressionType.equals(PsiType.INT) ||
                    expressionType.equals(PsiType.LONG);
        }

        private static boolean isFloatingPoint(PsiType expressionType) {
            return expressionType.equals(PsiType.FLOAT) ||
                    expressionType.equals(PsiType.DOUBLE);
        }
    }

    private class ImplicitNumericConversionVisitor
            extends BaseInspectionVisitor {

        @Override public void visitBinaryExpression(PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitConditionalExpression(
                PsiConditionalExpression expression) {
            super.visitConditionalExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitLiteralExpression(PsiLiteralExpression expression) {
            super.visitLiteralExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitPostfixExpression(PsiPostfixExpression expression) {
            super.visitPostfixExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitPrefixExpression(PsiPrefixExpression expression) {
            super.visitPrefixExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitReferenceExpression(
                PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitTypeCastExpression(PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitAssignmentExpression(
                PsiAssignmentExpression expression) {
            super.visitAssignmentExpression(expression);
            checkExpression(expression);
        }

        @Override public void visitParenthesizedExpression(
                PsiParenthesizedExpression expression) {
            super.visitParenthesizedExpression(expression);
            checkExpression(expression);
        }

        private void checkExpression(PsiExpression expression) {
            final PsiElement parent = expression.getParent();
            if (parent instanceof PsiParenthesizedExpression) {
                return;
            }
            final PsiType expressionType = expression.getType();
            if (expressionType == null) {
                return;
            }
            if (!ClassUtils.isPrimitiveNumericType(expressionType)) {
                return;
            }
            final PsiType expectedType =
                    ExpectedTypeUtils.findExpectedType(expression, true);
            if (!ClassUtils.isPrimitiveNumericType(expectedType)) {
                return;
            }
            if (expressionType.equals(expectedType)) {
                return;
            }
            if (m_ignoreWideningConversions && hasLowerPrecision(expressionType,
                    expectedType)) {
                return;
            }
            registerError(expression, expression, expressionType, expectedType);
        }
    }

    static boolean hasLowerPrecision(PsiType expressionType,
                                     PsiType expectedType) {
        final Integer operandPrecision = s_typePrecisions.get(expressionType);
        final Integer castPrecision = s_typePrecisions.get(expectedType);
        return operandPrecision <= castPrecision;
    }
}
