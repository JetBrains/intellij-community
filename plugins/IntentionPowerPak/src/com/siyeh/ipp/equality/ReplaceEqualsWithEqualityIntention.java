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
package com.siyeh.ipp.equality;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class ReplaceEqualsWithEqualityIntention extends Intention {

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new EqualsPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiMethodCallExpression call =
                (PsiMethodCallExpression)element;
        if (call == null) {
            return;
        }
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final PsiExpression target = methodExpression.getQualifierExpression();
        if (target == null) {
            return;
        }
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression arg = argumentList.getExpressions()[0];
        final PsiExpression strippedTarget =
                ParenthesesUtils.stripParentheses(target);
        if (strippedTarget == null) {
            return;
        }
        final PsiExpression strippedArg =
                ParenthesesUtils.stripParentheses(arg);
        if (strippedArg == null) {
            return;
        }
        final String strippedArgText;
        if (ParenthesesUtils.getPrecendence(strippedArg) >
                ParenthesesUtils.EQUALITY_PRECEDENCE) {
            strippedArgText = '(' + strippedArg.getText() + ')';
        } else {
            strippedArgText = strippedArg.getText();
        }
        final String strippedTargetText;
        if (ParenthesesUtils.getPrecendence(strippedTarget) >
                ParenthesesUtils.EQUALITY_PRECEDENCE) {
            strippedTargetText = '(' + strippedTarget.getText() + ')';
        } else {
            strippedTargetText = strippedTarget.getText();
        }
        replaceExpression(strippedTargetText + "==" + strippedArgText, call);
    }
}