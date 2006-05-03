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
package com.siyeh.ipp.junit;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class ReplaceAssertLiteralWithAssertEqualsIntention
        extends MutablyNamedIntention {

    protected String getTextForElement(PsiElement element) {
        final PsiMethodCallExpression call = (PsiMethodCallExpression)element;
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        @NonNls final String methodName = methodExpression.getReferenceName();
        assert methodName != null;
        final String postfix = methodName.substring("assert".length());
        final String literal = postfix.toLowerCase();
        if (args.length == 1) {
            return IntentionPowerPackBundle.message(
                    "replace.assert.literal.with.assert.equals.intention.name",
                    methodName, literal);
        } else {
            return IntentionPowerPackBundle.message(
                    "replace.assert.literal.with.assert.equals.intention.name1",
                    methodName, literal);
        }
    }

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new AssertLiteralPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiMethodCallExpression call =
                (PsiMethodCallExpression)element;
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final PsiElement qualifier =
                methodExpression.getQualifier();
        @NonNls final String methodName = methodExpression.getReferenceName();
        assert methodName != null;
        final String qualifierText;
        if (qualifier == null) {
            qualifierText = "";
        } else {
            qualifierText = qualifier.getText() + '.';
        }
        final String postfix = methodName.substring("assert".length());
        final String literal = postfix.toLowerCase();
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        @NonNls final String callString;
        if (arguments.length == 1) {
            callString = qualifierText + "assertEquals(" + literal + ", " +
                    arguments[0].getText() + ')';
        } else {
            callString =
                    qualifierText + "assertEquals(" + arguments[0].getText() +
                            ", " + literal + ", " + arguments[1].getText() + ')';
        }
        replaceExpression(callString, call);
    }
}