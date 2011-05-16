/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.concatenation;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.Nullable;

class StringConcatPredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(element instanceof PsiJavaToken){
            final PsiJavaToken token = (PsiJavaToken) element;
            final IElementType tokenType = token.getTokenType();
            if(!tokenType.equals(JavaTokenType.PLUS)){
                return false;
            }
        } else if(!(element instanceof PsiWhiteSpace)){
            return false;
        }
        final PsiElement parent = element.getParent();
        if(!(parent instanceof PsiBinaryExpression)){
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression) parent;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if(!tokenType.equals(JavaTokenType.PLUS)){
            return false;
        }
        final PsiType type = binaryExpression.getType();
        if (type == null || !type.equalsToText("java.lang.String")) {
            return false;
        }
        final PsiExpression rhs = binaryExpression.getROperand();
        if (rhs == null) {
            return false;
        }
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rightMostExpression = getRightmostExpression(lhs);
        if (rightMostExpression instanceof PsiPrefixExpression) {
            final PsiType prefixExpressionType = rightMostExpression.getType();
            if (prefixExpressionType == null ||
                    prefixExpressionType.equalsToText("java.lang.String")) {
                return false;
            }
        }
        return PsiUtil.isConstantExpression(rhs) &&
                PsiUtil.isConstantExpression(rightMostExpression);
    }

    @Nullable private static PsiExpression getRightmostExpression(
            PsiExpression expression){
        if (expression instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)expression;
            final PsiExpression rhs = binaryExpression.getROperand();
            return getRightmostExpression(rhs);
        }
        return expression;
    }
}