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
package com.siyeh.ipp.shift;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;

public class ReplaceShiftWithMultiplyIntention extends MutablyNamedIntention {

    protected String getTextForElement(PsiElement element) {
        if (element instanceof PsiBinaryExpression) {
            final PsiBinaryExpression exp = (PsiBinaryExpression)element;
            final PsiJavaToken sign = exp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final String operatorString;
            if (tokenType.equals(JavaTokenType.LTLT)) {
                operatorString = "*";
            } else {
                operatorString = "/";
            }
            return IntentionPowerPackBundle.message(
                    "replace.some.operator.with.other.intention.name",
                    sign.getText(), operatorString);
        } else {
            final PsiAssignmentExpression exp =
                    (PsiAssignmentExpression)element;
            final PsiJavaToken sign = exp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final String assignString;
            if (JavaTokenType.LTLTEQ.equals(tokenType)) {
                assignString = "*=";
            } else {
                assignString = "/=";
            }
            return IntentionPowerPackBundle.message(
                    "replace.some.operator.with.other.intention.name",
                    sign.getText(), assignString);
        }
    }

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ShiftByLiteralPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        if (element instanceof PsiBinaryExpression) {
            replaceShiftWithMultiplyOrDivide(element);
        } else {
            replaceShiftAssignWithMultiplyOrDivideAssign(element);
        }
    }

    private static void replaceShiftAssignWithMultiplyOrDivideAssign(
            PsiElement element)
            throws IncorrectOperationException {
        final PsiAssignmentExpression exp =
                (PsiAssignmentExpression)element;
        final PsiExpression lhs = exp.getLExpression();
        final PsiExpression rhs = exp.getRExpression();
        final PsiJavaToken sign = exp.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        final String assignString;
        if (tokenType.equals(JavaTokenType.LTLTEQ)) {
            assignString = "*=";
        } else {
            assignString = "/=";
        }
        final String expString =
                lhs.getText() + assignString + ShiftUtils.getExpBase2(rhs);
        replaceExpression(expString, exp);
    }

    private static void replaceShiftWithMultiplyOrDivide(PsiElement element)
            throws IncorrectOperationException {
        final PsiBinaryExpression exp =
                (PsiBinaryExpression)element;
        final PsiExpression lhs = exp.getLOperand();
        final PsiExpression rhs = exp.getROperand();
        final PsiJavaToken sign = exp.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        final String operatorString;
        if (tokenType.equals(JavaTokenType.LTLT)) {
            operatorString = "*";
        } else {
            operatorString = "/";
        }
        final String lhsText;
        if (ParenthesesUtils.getPrecedence(lhs) >
                ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE) {
            lhsText = '(' + lhs.getText() + ')';
        } else {
            lhsText = lhs.getText();
        }
        String expString =
                lhsText + operatorString + ShiftUtils.getExpBase2(rhs);
        final PsiElement parent = exp.getParent();
        if (parent instanceof PsiExpression) {
            if (!(parent instanceof PsiParenthesizedExpression) &&
                    ParenthesesUtils.getPrecedence((PsiExpression)parent) <
                            ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE) {
                expString = '(' + expString + ')';
            }
        }
        replaceExpression(expString, exp);
    }
}