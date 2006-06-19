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
package com.siyeh.ipp.opassign;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;

public class ReplaceWithOperatorAssignmentIntention
        extends MutablyNamedIntention {

    public String getTextForElement(PsiElement element) {
        final PsiAssignmentExpression exp = (PsiAssignmentExpression)element;
        final PsiBinaryExpression rhs =
                (PsiBinaryExpression)exp.getRExpression();
        assert rhs != null;
        final PsiJavaToken sign = rhs.getOperationSign();
        final String operator = sign.getText();
        return IntentionPowerPackBundle.message(
                "replace.assignment.with.operator.assignment.intention.name",
                operator);
    }

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new AssignmentExpressionReplaceableWithOperatorAssigment();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiAssignmentExpression exp = (PsiAssignmentExpression)element;
        final PsiBinaryExpression rhs =
                (PsiBinaryExpression)exp.getRExpression();
        final PsiExpression lhs = exp.getLExpression();
        assert rhs != null;
        final PsiJavaToken sign = rhs.getOperationSign();
        final String operand = sign.getText();
        final PsiExpression rhsrhs = rhs.getROperand();
        assert rhsrhs != null;
        final String expString =
                lhs.getText() + operand + '=' + rhsrhs.getText();
        replaceExpression(expString, exp);
    }
}