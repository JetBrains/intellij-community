/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ConditionalUtils;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class ReplaceIfWithConditionalIntention extends Intention {

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ReplaceIfWithConditionalPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiJavaToken token = (PsiJavaToken)element;
        final PsiIfStatement ifStatement = (PsiIfStatement)token.getParent();
        if (ifStatement == null) {
            return;
        }
        if (ReplaceIfWithConditionalPredicate.isReplaceableAssignment(
                ifStatement)) {
            final PsiExpression condition = ifStatement.getCondition();
            if (condition == null) {
                return;
            }
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            final PsiExpressionStatement strippedThenBranch =
                    (PsiExpressionStatement)ConditionalUtils.stripBraces(
                            thenBranch);
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            final PsiExpressionStatement strippedElseBranch =
                    (PsiExpressionStatement)ConditionalUtils.stripBraces(
                            elseBranch);
            final PsiAssignmentExpression thenAssign =
                    (PsiAssignmentExpression)strippedThenBranch.getExpression();
            final PsiAssignmentExpression elseAssign =
                    (PsiAssignmentExpression)strippedElseBranch.getExpression();
            final PsiExpression lhs = thenAssign.getLExpression();
            final String lhsText = lhs.getText();
            final PsiJavaToken sign = thenAssign.getOperationSign();
            final String operator = sign.getText();
            final PsiExpression thenRhs = thenAssign.getRExpression();
            if (thenRhs == null) {
                return;
            }
            final String thenValue;
            if (ParenthesesUtils.getPrecendence(thenRhs)
                    <= ParenthesesUtils.CONDITIONAL_PRECEDENCE) {
                thenValue = thenRhs.getText();
            } else {
                thenValue = '(' + thenRhs.getText() + ')';
            }
            final PsiExpression elseRhs = elseAssign.getRExpression();
            if (elseRhs == null) {
                return;
            }
            final String elseValue;
            if (ParenthesesUtils.getPrecendence(elseRhs)
                    <= ParenthesesUtils.CONDITIONAL_PRECEDENCE) {
                elseValue = elseRhs.getText();
            } else {
                elseValue = '(' + elseRhs.getText() + ')';
            }
            final String conditionText;
            if (ParenthesesUtils.getPrecendence(condition)
                    <= ParenthesesUtils.CONDITIONAL_PRECEDENCE) {
                conditionText = condition.getText();
            } else {
                conditionText = '(' + condition.getText() + ')';
            }

            replaceStatement(lhsText + operator + conditionText + '?' +
                    thenValue + ':' + elseValue + ';',
                    ifStatement);
        } else if (ReplaceIfWithConditionalPredicate.isReplaceableReturn(ifStatement)) {
            final PsiExpression condition = ifStatement.getCondition();
            if (condition == null) {
                return;
            }
            final PsiStatement thenBranch = ifStatement.getThenBranch();
            final PsiReturnStatement thenReturn =
                    (PsiReturnStatement)ConditionalUtils.stripBraces(thenBranch);
            final PsiStatement elseBranch = ifStatement.getElseBranch();
            final PsiReturnStatement elseReturn =
                    (PsiReturnStatement)ConditionalUtils.stripBraces(elseBranch);

            final PsiExpression thenReturnValue = thenReturn.getReturnValue();
            if (thenReturnValue == null) {
                return;
            }
            final String thenValue;
            if (ParenthesesUtils.getPrecendence(thenReturnValue)
                    <= ParenthesesUtils.CONDITIONAL_PRECEDENCE) {
                thenValue = thenReturnValue.getText();
            } else {
                thenValue = '(' + thenReturnValue.getText() + ')';
            }
            final PsiExpression elseReturnValue = elseReturn.getReturnValue();
            if (elseReturnValue == null) {
                return;
            }
            final String elseValue;
            if (ParenthesesUtils.getPrecendence(elseReturnValue)
                    <= ParenthesesUtils.CONDITIONAL_PRECEDENCE) {
                elseValue = elseReturnValue.getText();
            } else {
                elseValue = '(' + elseReturnValue.getText() + ')';
            }
            final String conditionText;
            if (ParenthesesUtils.getPrecendence(condition)
                    <= ParenthesesUtils.CONDITIONAL_PRECEDENCE) {
                conditionText = condition.getText();
            } else {
                conditionText = '(' + condition.getText() + ')';
            }

            replaceStatement("return " + conditionText + '?' + thenValue + ':' +
                    elseValue + ';', ifStatement);
        } else if (ReplaceIfWithConditionalPredicate.isReplaceableImplicitReturn(
                ifStatement)) {
            final PsiExpression condition = ifStatement.getCondition();
            if (condition == null) {
                return;
            }
            final PsiStatement rawThenBranch = ifStatement.getThenBranch();
            final PsiReturnStatement thenBranch =
                    (PsiReturnStatement)ConditionalUtils.stripBraces(rawThenBranch);
            final PsiReturnStatement elseBranch =
                    PsiTreeUtil.getNextSiblingOfType(ifStatement, PsiReturnStatement.class);

            final PsiExpression thenReturnValue = thenBranch.getReturnValue();
            if (thenReturnValue == null) {
                return;
            }
            final String thenValue;
            if (ParenthesesUtils.getPrecendence(thenReturnValue)
                    <= ParenthesesUtils.CONDITIONAL_PRECEDENCE) {
                thenValue = thenReturnValue.getText();
            } else {
                thenValue = '(' + thenReturnValue.getText() + ')';
            }
            if (elseBranch == null) {
                return;
            }
            final PsiExpression elseReturnValue = elseBranch.getReturnValue();
            if (elseReturnValue == null) {
                return;
            }
            final String elseValue;
            if (ParenthesesUtils.getPrecendence(elseReturnValue)
                    <= ParenthesesUtils.CONDITIONAL_PRECEDENCE) {
                elseValue = elseReturnValue.getText();
            } else {
                elseValue = '(' + elseReturnValue.getText() + ')';
            }
            final String conditionText;
            if (ParenthesesUtils.getPrecendence(condition)
                    <= ParenthesesUtils.CONDITIONAL_PRECEDENCE) {
                conditionText = condition.getText();
            } else {
                conditionText = '(' + condition.getText() + ')';
            }

            replaceStatement("return " + conditionText + '?' + thenValue + ':' +
                    elseValue + ';',
                    ifStatement);
            elseBranch.delete();
        }
    }
}
