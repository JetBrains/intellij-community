/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ipp.equality;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ReplaceEqualityWithSafeEqualsIntention extends Intention {

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ObjectEqualityPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiBinaryExpression exp =
                (PsiBinaryExpression)element;
        final PsiExpression lhs = exp.getLOperand();
        final PsiExpression rhs = exp.getROperand();
        if (rhs == null) {
            return;
        }
        final PsiExpression strippedLhs =
                ParenthesesUtils.stripParentheses(lhs);
        if (strippedLhs == null) {
            return;
        }
        final PsiExpression strippedRhs =
                ParenthesesUtils.stripParentheses(rhs);
        if (strippedRhs == null) {
            return;
        }
        final String lhsText = strippedLhs.getText();
        final String rhsText = strippedRhs.getText();
        final PsiJavaToken operationSign = exp.getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
        final String signText = operationSign.getText();
        @NonNls final StringBuilder buffer = new StringBuilder(lhsText);
        buffer.append("==null?");
        buffer.append(rhsText);
        buffer.append(signText);
        buffer.append(" null:");
        if (tokenType.equals(JavaTokenType.NE)) {
            buffer.append('!');
        }
        if (ParenthesesUtils.getPrecedence(strippedLhs) >
                ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
            buffer.append('(');
            buffer.append(lhsText);
            buffer.append(')');
        } else {
            buffer.append(lhsText);
        }
        buffer.append(".equals(");
        buffer.append(rhsText);
        buffer.append(')');
        replaceExpression(buffer.toString(), exp);
    }
}