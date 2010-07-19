/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ExpandBooleanIntention extends Intention {

    @Override
    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ExpandBooleanPredicate();
    }

    @Override
    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiStatement containingStatement =
                PsiTreeUtil.getParentOfType(element, PsiStatement.class);
        if (containingStatement == null) {
            return;
        }
        if (ExpandBooleanPredicate.isBooleanAssignment(containingStatement)) {
            final PsiExpressionStatement assignmentStatement =
                    (PsiExpressionStatement)containingStatement;
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression)assignmentStatement.getExpression();
            final PsiExpression rhs = assignmentExpression.getRExpression();
            if (rhs == null) {
                return;
            }
            final PsiExpression lhs = assignmentExpression.getLExpression();
            if (ErrorUtil.containsDeepError(lhs) ||
                    ErrorUtil.containsDeepError(rhs)) {
                return;
            }
            final String rhsText = rhs.getText();
            final String lhsText = lhs.getText();
            final PsiJavaToken sign = assignmentExpression.getOperationSign();
            final String signText = sign.getText();
            final String conditionText;
            if (signText.length() == 2) {
                conditionText = lhsText + signText.charAt(0) + rhsText;
            } else {
                conditionText = rhsText;
            }

            @NonNls final String statement =
                    "if(" + conditionText + ") " + lhsText + " = true; else " +
                            lhsText + " = false;";
            replaceStatement(statement, containingStatement);
        } else if (ExpandBooleanPredicate.isBooleanReturn(
                containingStatement)) {
            final PsiReturnStatement returnStatement =
                    (PsiReturnStatement)containingStatement;
            final PsiExpression returnValue = returnStatement.getReturnValue();
            if (returnValue == null) {
                return;
            }
            if (ErrorUtil.containsDeepError(returnValue)) {
                return;
            }
            final String valueText = returnValue.getText();
            @NonNls final String statement =
                    "if(" + valueText + ") return true; else return false;";
            replaceStatement(statement, containingStatement);
        }
    }
}