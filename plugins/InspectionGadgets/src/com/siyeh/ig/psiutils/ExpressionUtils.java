/*
 * Copyright 2005-2006 Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

public class ExpressionUtils {

    private ExpressionUtils() {}

    public static boolean isEmptyStringLiteral(
            @Nullable PsiExpression expression) {
        if (!(expression instanceof PsiLiteralExpression)) {
            return false;
        }
        final String text = expression.getText();
        return "\"\"".equals(text);
    }

    public static boolean isNullLiteral(@Nullable PsiExpression expression) {
        if (!(expression instanceof PsiLiteralExpression)) {
            return false;
        }
        final String text = expression.getText();
        return PsiKeyword.NULL.equals(text);
    }

    public static boolean isZero(PsiExpression expression) {
        final PsiType expressionType = expression.getType();
        final Object value = ConstantExpressionUtil.computeCastTo(expression,
                expressionType);
        if(value == null){
            return false;
        }
        if(value instanceof Double && ((Double) value).doubleValue() == 0.0) {
            return true;
        }
        if(value instanceof Integer && ((Integer) value).intValue() == 0){
            return true;
        }
        if(value instanceof Long && ((Long) value).longValue() == 0L){
            return true;
        }
        if(value instanceof Short && ((Short) value).shortValue() == 0){
            return true;
        }
        if(value instanceof Character && ((Character) value).charValue() == 0){
            return true;
        }
        return value instanceof Byte && ((Byte) value).byteValue() == 0;
    }

    public static boolean isOne(PsiExpression expression) {
        final Object value = ConstantExpressionUtil.computeCastTo(
                expression, PsiType.INT);
        return value instanceof Integer && ((Integer) value).intValue() == 1;
    }

    public static boolean isNegation(@Nullable PsiExpression condition,
                                     boolean ignoreNegatedNullComparison) {
        if (condition instanceof PsiPrefixExpression) {
            final PsiPrefixExpression prefixExpression =
                    (PsiPrefixExpression)condition;
            final PsiJavaToken sign = prefixExpression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            return tokenType.equals(JavaTokenType.EXCL);
        } else if (condition instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)condition;
            final PsiJavaToken sign = binaryExpression.getOperationSign();
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            if (rhs == null) {
                return false;
            }
            final IElementType tokenType = sign.getTokenType();
            if (tokenType.equals(JavaTokenType.NE)) {
                if (ignoreNegatedNullComparison) {
                    final String lhsText = lhs.getText();
                    final String rhsText = rhs.getText();
                    return !PsiKeyword.NULL.equals(lhsText) &&
                            !PsiKeyword.NULL.equals(rhsText);
                } else {
                    return true;
                }
            } else {
                return false;
            }
        } else if (condition instanceof PsiParenthesizedExpression) {
            final PsiExpression expression =
                    ((PsiParenthesizedExpression)condition).getExpression();
            return isNegation(expression, ignoreNegatedNullComparison);
        } else {
            return false;
        }
    }

    public static boolean isOffsetArrayAccess(PsiExpression expression,
                                               PsiLocalVariable variable) {
        final PsiExpression strippedExpression =
                ParenthesesUtils.stripParentheses(expression);
        if (!(strippedExpression instanceof PsiArrayAccessExpression)) {
            return false;
        }
        final PsiArrayAccessExpression arrayExpression =
                (PsiArrayAccessExpression)strippedExpression;
        final PsiExpression index = arrayExpression.getIndexExpression();
        if (index == null) {
            return false;
        }
        return expressionIsOffsetVariableLookup(index, variable);
    }

    private static boolean expressionIsOffsetVariableLookup(
            PsiExpression expression, PsiLocalVariable variable) {
        final PsiExpression strippedExpression =
                ParenthesesUtils.stripParentheses(expression);
        if (VariableAccessUtils.evaluatesToVariable(strippedExpression,
                variable)) {
            return true;
        }
        if (!(strippedExpression instanceof PsiBinaryExpression)) {
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression)strippedExpression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if (!JavaTokenType.PLUS.equals(tokenType) &&
                !JavaTokenType.MINUS.equals(tokenType)) {
            return false;
        }
        final PsiExpression lhs = binaryExpression.getLOperand();
        if (expressionIsOffsetVariableLookup(lhs, variable)) {
            return true;
        }
        final PsiExpression rhs = binaryExpression.getROperand();
        return expressionIsOffsetVariableLookup(rhs, variable) &&
                !JavaTokenType.MINUS.equals(tokenType);
    }

    public static boolean isComparison(PsiExpression expression,
                                        PsiLocalVariable variable) {
        expression =
                ParenthesesUtils.stripParentheses(expression);
        if (!(expression instanceof PsiBinaryExpression)) {
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression)expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if (tokenType.equals(JavaTokenType.LT)) {
            PsiExpression lhs = binaryExpression.getLOperand();
            lhs = ParenthesesUtils.stripParentheses(lhs);
            return VariableAccessUtils.evaluatesToVariable(lhs, variable);
        } else if (tokenType.equals(JavaTokenType.GT)) {
            PsiExpression rhs = binaryExpression.getROperand();
            rhs = ParenthesesUtils.stripParentheses(rhs);
            return VariableAccessUtils.evaluatesToVariable(rhs, variable);
        }
        return false;
    }
}