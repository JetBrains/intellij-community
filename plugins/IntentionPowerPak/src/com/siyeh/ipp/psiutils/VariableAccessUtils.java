/*
 * Copyright 2009 Bas Leijdekkers
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
package com.siyeh.ipp.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VariableAccessUtils {

    private VariableAccessUtils() {
    }

    public static boolean isComparisonWithVariable(
            @Nullable PsiExpression expression,
            @NotNull PsiVariable variable) {
        if (!(expression instanceof PsiBinaryExpression)) {
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression)expression;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        if (!ComparisonUtils.isComparison(sign)) {
            return false;
        }
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        if (rhs == null) {
            return false;
        }
        if (evaluatesToVariable(lhs, variable)) {
            return true;
        } else if (evaluatesToVariable(rhs, variable)) {
            return true;
        }
        return false;
    }

    public static boolean isIncrementOfVariable(
            @Nullable PsiStatement statement,
            @NotNull PsiVariable variable) {
        if (!(statement instanceof PsiExpressionStatement)) {
            return false;
        }
        final PsiExpressionStatement expressionStatement =
                (PsiExpressionStatement)statement;
        PsiExpression expression = expressionStatement.getExpression();
        expression = ParenthesesUtils.stripParentheses(expression);
        if (expression instanceof PsiPrefixExpression) {
            final PsiPrefixExpression prefixExpression =
                    (PsiPrefixExpression)expression;
            final PsiJavaToken sign = prefixExpression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS)) {
                return false;
            }
            final PsiExpression operand = prefixExpression.getOperand();
            return evaluatesToVariable(operand, variable);
        } else if (expression instanceof PsiPostfixExpression) {
            final PsiPostfixExpression postfixExpression =
                    (PsiPostfixExpression)expression;
            final PsiJavaToken sign = postfixExpression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (!tokenType.equals(JavaTokenType.PLUSPLUS)) {
                return false;
            }
            final PsiExpression operand = postfixExpression.getOperand();
            return evaluatesToVariable(operand, variable);
        } else if (expression instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) expression;
            final IElementType tokenType =
                    assignmentExpression.getOperationTokenType();
            PsiExpression lhs = assignmentExpression.getLExpression();
            lhs = ParenthesesUtils.stripParentheses(lhs);
            if (!evaluatesToVariable(lhs, variable)) {
                return false;
            }
            PsiExpression rhs = assignmentExpression.getRExpression();
            rhs = ParenthesesUtils.stripParentheses(rhs);
            if (tokenType == JavaTokenType.EQ) {
                if (!(rhs instanceof PsiBinaryExpression)) {
                    return false;
                }
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) rhs;
                PsiExpression lOperand = binaryExpression.getLOperand();
                lOperand = ParenthesesUtils.stripParentheses(lOperand);
                PsiExpression rOperand = binaryExpression.getROperand();
                rOperand = ParenthesesUtils.stripParentheses(rOperand);
                if (evaluatesToVariable(rOperand, variable)) {
                    return true;
                } else if (evaluatesToVariable(lOperand, variable)) {
                    return true;
                }
            } else if (tokenType == JavaTokenType.PLUSEQ) {
                return true;
            }
        }
        return false;
    }

    public static boolean evaluatesToVariable(
            @Nullable PsiExpression expression,
            @NotNull PsiVariable variable) {
        final PsiExpression strippedExpression =
                ParenthesesUtils.stripParentheses(expression);
        if(strippedExpression == null){
            return false;
        }
        if (!(expression instanceof PsiReferenceExpression)) {
            return false;
        }
        final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression) expression;
        final PsiElement referent = referenceExpression.resolve();
        return variable.equals(referent);
    }
}