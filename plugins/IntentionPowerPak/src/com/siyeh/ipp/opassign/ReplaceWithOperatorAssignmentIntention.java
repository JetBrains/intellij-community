/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ReplaceWithOperatorAssignmentIntention
        extends MutablyNamedIntention {

    public String getTextForElement(PsiElement element) {
        final PsiAssignmentExpression assignmentExpression =
                (PsiAssignmentExpression)element;
        final PsiExpression rhs = assignmentExpression.getRExpression();
        final PsiBinaryExpression expression =
                (PsiBinaryExpression)PsiUtil.deparenthesizeExpression(rhs);
        assert expression != null;
        final PsiJavaToken sign = expression.getOperationSign();
        final String operator = sign.getText();
        return IntentionPowerPackBundle.message(
                "replace.assignment.with.operator.assignment.intention.name",
                operator);
    }

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new AssignmentExpressionReplaceableWithOperatorAssigment();
    }

    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiAssignmentExpression expression =
                (PsiAssignmentExpression)element;
        final PsiExpression rhs =
		        expression.getRExpression();
	    final PsiBinaryExpression binaryExpression =
			    (PsiBinaryExpression)PsiUtil.deparenthesizeExpression(rhs);
        final PsiExpression lhs = expression.getLExpression();
        assert rhs != null;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final String operand = sign.getText();
        final PsiExpression binaryRhs = binaryExpression.getROperand();
        assert binaryRhs != null;
        final String newExpression =
                lhs.getText() + operand + '=' + binaryRhs.getText();
        replaceExpression(newExpression, expression);
    }
}