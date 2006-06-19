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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class ReplaceEqualityWithEqualsIntention extends Intention {

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
        final PsiJavaToken operationSign = exp.getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
        @NonNls final String expString;
        if (tokenType.equals(JavaTokenType.EQEQ)) {
            if (ParenthesesUtils.getPrecendence(strippedLhs) >
                    ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
                expString = '(' + strippedLhs.getText() + ").equals(" +
                        strippedRhs.getText() + ')';
            } else {
                expString = strippedLhs.getText() + ".equals(" +
                        strippedRhs.getText() + ')';
            }
        } else {
            if (ParenthesesUtils.getPrecendence(strippedLhs) >
                    ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
                expString = "!(" + strippedLhs.getText() + ").equals(" +
                        strippedRhs.getText() + ')';
            } else {
                expString = '!' + strippedLhs.getText() + ".equals(" +
                        strippedRhs.getText() + ')';
            }
        }
        replaceExpression(expString, exp);
    }
}