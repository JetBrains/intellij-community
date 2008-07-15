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

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiInstanceOfExpression;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;
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
        if (parent instanceof PsiExpression) {
            final PsiExpression expression = (PsiExpression)parent;
            if (needsParentheses(expression, tokenType)) {
                return true;
            }
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
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)expression;
            final IElementType expressionTokenType =
                    binaryExpression.getOperationTokenType();
            if (!tokenType.equals(expressionTokenType)) {
                return true;
            }
        } else if (expression instanceof PsiInstanceOfExpression) {
            return true;
        }
        return false;
    }
}