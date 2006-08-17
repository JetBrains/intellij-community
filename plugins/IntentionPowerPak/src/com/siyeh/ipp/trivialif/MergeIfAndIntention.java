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
package com.siyeh.ipp.trivialif;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ConditionalUtils;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class MergeIfAndIntention extends Intention {

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new MergeIfAndPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiJavaToken token =
                (PsiJavaToken)element;
        final PsiIfStatement parentStatement =
                (PsiIfStatement)token.getParent();
        if (parentStatement == null) {
            return;
        }
        final PsiStatement parentThenBranch = parentStatement.getThenBranch();
        final PsiIfStatement childStatement =
                (PsiIfStatement)ConditionalUtils.stripBraces(parentThenBranch);
        final PsiExpression childCondition = childStatement.getCondition();
        if (childCondition == null) {
            return;
        }
        final String childConditionText;
        if (ParenthesesUtils.getPrecedence(childCondition)
                > ParenthesesUtils.AND_PRECEDENCE) {
            childConditionText = '(' + childCondition.getText() + ')';
        } else {
            childConditionText = childCondition.getText();
        }

        final PsiExpression parentCondition = parentStatement.getCondition();
        if (parentCondition == null) {
            return;
        }
        final String parentConditionText;
        if (ParenthesesUtils.getPrecedence(parentCondition)
                > ParenthesesUtils.AND_PRECEDENCE) {
            parentConditionText = '(' + parentCondition.getText() + ')';
        } else {
            parentConditionText = parentCondition.getText();
        }
        final PsiStatement childThenBranch = childStatement.getThenBranch();
        if (childThenBranch == null) {
            return;
        }
        @NonNls final String statement = "if(" + parentConditionText + "&&" +
                childConditionText + ')' + childThenBranch.getText();
        replaceStatement(statement, parentStatement);
    }
}