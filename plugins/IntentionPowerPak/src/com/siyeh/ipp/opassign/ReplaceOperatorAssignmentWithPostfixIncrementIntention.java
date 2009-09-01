/*
 * Copyright 2009 Bas Leijdekkers
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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;

public class ReplaceOperatorAssignmentWithPostfixIncrementIntention
        extends MutablyNamedIntention {

    @Override
    protected String getTextForElement(PsiElement element) {
        final PsiAssignmentExpression assignment =
                (PsiAssignmentExpression)element;
        final PsiExpression expression = assignment.getLExpression();
        final String expressionText = expression.getText();
        return IntentionPowerPackBundle.message(
                "replace.operator.assignment.with.postfix.increment.intention.name",
                expressionText);
    }

    @NotNull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new ReplaceOperatorAssignmentWithPostfixIncrementPredicate();
    }

    @Override
    protected void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiAssignmentExpression assignment =
                (PsiAssignmentExpression)element;
        final PsiExpression expression = assignment.getLExpression();
        final String expressionText = expression.getText();
        final IElementType tokenType = assignment.getOperationTokenType();
        final String newExpressionText;
        if (JavaTokenType.PLUSEQ.equals(tokenType)) {
            newExpressionText = expressionText + "++";
        } else if (JavaTokenType.MINUSEQ.equals(tokenType)) {
            newExpressionText = expressionText + "--";
        } else {
            return;
        }
        replaceExpression(newExpressionText, assignment);
    }
}