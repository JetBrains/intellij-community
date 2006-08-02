/*
 * Copyright 2006 Bas Leijdekkers
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
package com.siyeh.ipp.commutative;

import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.IntentionPowerPackBundle;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class FlipMethodCallArgumentsIntention extends MutablyNamedIntention {

    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new FlipMethodCallArgumentsPredicate();
    }

    protected String getTextForElement(PsiElement element) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) element;
        final PsiExpressionList expressionList = methodCallExpression.getArgumentList();
        final PsiExpression[] expressions = expressionList.getExpressions();
        final PsiExpression firstExpression = expressions[0];
        final PsiExpression secondExpression = expressions[1];
        return IntentionPowerPackBundle.message("flip.method.call.arguments.intention.name",
                firstExpression.getText(), secondExpression.getText());
    }

    protected void processIntention(@NotNull PsiElement element) throws IncorrectOperationException {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) element;
        final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        final PsiExpression firstArgument = arguments[0];
        final PsiExpression secondArgument = arguments[1];
        final String firstArgumentText = firstArgument.getText();
        final String secondArgumentText = secondArgument.getText();
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final String methodText = methodExpression.getText();
        final String newExpression = methodText + '(' + secondArgumentText + ", " + firstArgumentText + ')';
        replaceExpression(newExpression, methodCallExpression);
    }
}
