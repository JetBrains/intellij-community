/*
 * Copyright 2006-2008 Bas Leijdekkers
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
package com.siyeh.ipp.parenthesis;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

class AddClarifyingParenthesesPredicate implements PsiElementPredicate {

    public boolean satisfiedBy(@NotNull PsiElement element) {
        if (!(element instanceof PsiBinaryExpression)) {
            return element instanceof PsiInstanceOfExpression &&
                    element.getParent()instanceof PsiBinaryExpression;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression)element;
        final IElementType tokenType = binaryExpression.getOperationTokenType();
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiBinaryExpression) {
            final PsiBinaryExpression parentBinaryExpression =
                    (PsiBinaryExpression)parent;
            final IElementType parentTokenType =
                    parentBinaryExpression.getOperationTokenType();
            if (!tokenType.equals(parentTokenType)) {
                return true;
            } else if (!ParenthesesUtils.isCommutativeBinaryOperator(
                    tokenType)) {
                return true;
            }
        } else if (parent instanceof PsiInstanceOfExpression) {
            return true;
        }
        final PsiExpression lhs = binaryExpression.getLOperand();
        if (needsParentheses(lhs, tokenType)) {
            return true;
        }
        final PsiExpression rhs = binaryExpression.getROperand();
        return needsParentheses(rhs, tokenType);
    }

    private static boolean needsParentheses(PsiExpression expression,
                                            IElementType tokenType) {
        if (expression instanceof PsiBinaryExpression) {
            final PsiBinaryExpression lhsBinaryExpression =
                    (PsiBinaryExpression)expression;
            final IElementType lhsTokenType =
                    lhsBinaryExpression.getOperationTokenType();
            if (!tokenType.equals(lhsTokenType)) {
                return true;
            } else if (!ParenthesesUtils.isCommutativeBinaryOperator(
                    tokenType)) {
                return true;
            }
        } else if (expression instanceof PsiInstanceOfExpression) {
            return true;
        }
        return false;
    }
}