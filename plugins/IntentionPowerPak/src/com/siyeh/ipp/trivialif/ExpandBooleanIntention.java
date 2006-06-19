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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class ExpandBooleanIntention extends Intention {

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ExpandBooleanPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiJavaToken token = (PsiJavaToken)element;
        final PsiStatement containingStatement =
                PsiTreeUtil.getParentOfType(token, PsiStatement.class);
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
            final String rhsText = rhs.getText();
            final PsiExpression lhs = assignmentExpression.getLExpression();
            final String lhsText = lhs.getText();
            @NonNls final String statement =
                    "if(" + rhsText + "){" + lhsText + " = true;}else{" +
                            lhsText + " = false;}";
            replaceStatement(statement, containingStatement);
        } else if (ExpandBooleanPredicate.isBooleanReturn(
                containingStatement)) {
            final PsiReturnStatement returnStatement =
                    (PsiReturnStatement)containingStatement;
            final PsiExpression returnValue = returnStatement.getReturnValue();
            if (returnValue == null) {
                return;
            }
            final String valueText = returnValue.getText();
            @NonNls final String statement =
                    "if(" + valueText + "){return true;}else{return false;}";
            replaceStatement(statement, containingStatement);
        }
    }
}