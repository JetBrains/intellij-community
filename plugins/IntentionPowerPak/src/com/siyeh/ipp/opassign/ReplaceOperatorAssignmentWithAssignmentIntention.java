/*
 * Copyright 2007 Bas Leijdekkers
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
package com.siyeh.ipp.opassign;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class ReplaceOperatorAssignmentWithAssignmentIntention
        extends MutablyNamedIntention {

    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new OperatorAssignmentPredicate();
    }

    protected String getTextForElement(PsiElement element) {
        final PsiAssignmentExpression assignmentExpression =
                (PsiAssignmentExpression)element;
        final PsiJavaToken sign = assignmentExpression.getOperationSign();
        final String operator = sign.getText();
        return IntentionPowerPackBundle.message(
                "replace.operator.assignment.with.assignment.intention.name",
                operator);
    }

    protected void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiAssignmentExpression assignmentExpression =
                (PsiAssignmentExpression)element;
        final PsiJavaToken sign = assignmentExpression.getOperationSign();
        final PsiExpression lhs = assignmentExpression.getLExpression();
        final PsiExpression rhs = assignmentExpression.getRExpression();
        final String operand = sign.getText();
        final String newOperand = operand.substring(0, operand.length() - 1);
        final String lhsText = lhs.getText();
        final String rhsText;
        if (rhs == null) {
            rhsText = "";
        } else {
            rhsText = rhs.getText();
        }
        if (rhs instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)rhs;
            final PsiJavaToken javaToken = binaryExpression.getOperationSign();
            final int precedence1 =
                    ParenthesesUtils.getPrecedenceForBinaryOperator(javaToken);
            final int precedence2 =
                    ParenthesesUtils.getPrecedenceForBinaryOperator(newOperand);
            if (precedence1 > precedence2) {
                final String expString;
                if (needsCast(rhs)) {
                    expString = lhsText + "=(int)" + lhsText + newOperand
                                + '(' + rhsText + "))";
                } else {
                    expString = lhsText + '=' + lhsText + newOperand
                                + '(' + rhsText + ')';
                }
                replaceExpression(expString, assignmentExpression);
                return;
            }
        }
        final String expString;
        if (needsCast(rhs)) {
            expString = lhsText + "=(int)(" + lhsText + newOperand + rhsText
                        + ')';
        } else {
            expString = lhsText + '=' + lhsText + newOperand + rhsText;
        }
        replaceExpression(expString, assignmentExpression);
    }

    private static boolean needsCast(PsiExpression expression) {
        final PsiType type = expression.getType();
        return PsiType.LONG.equals(type) || PsiType.DOUBLE.equals(type) ||
               PsiType.FLOAT.equals(type);
    }
}