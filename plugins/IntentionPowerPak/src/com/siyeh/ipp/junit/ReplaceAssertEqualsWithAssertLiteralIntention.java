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

public class ReplaceAssertEqualsWithAssertLiteralIntention
        extends MutablyNamedIntention {

    protected String getTextForElement(PsiElement element) {

        final PsiMethodCallExpression call = (PsiMethodCallExpression)element;
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        final String assertString;
        if (args.length == 2) {
            final String argText = args[0].getText();
            assertString = getAssertString(argText);
        } else {
            final String argText = args[1].getText();
            assertString = getAssertString(argText);
        }
        return IntentionPowerPackBundle.message(
                "replace.assert.equals.with.assert.literal.intention.name",
                assertString);
    }

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new AssertEqualsWithLiteralPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiMethodCallExpression call =
                (PsiMethodCallExpression)element;
        final PsiReferenceExpression expression = call.getMethodExpression();
        final PsiElement qualifier = expression.getQualifier();
        final String qualifierText;
        if (qualifier == null) {
            qualifierText = "";
        } else {
            qualifierText = qualifier.getText() + '.';
        }
        final PsiExpressionList argumentList = call.getArgumentList();
        final PsiExpression[] args = argumentList.getExpressions();
        final String callString;
        if (args.length == 2) {
            @NonNls final String argText = args[0].getText();
            final PsiExpression otherArg;
            if ("true".equals(argText) ||
                    "false".equals(argText) ||
                    "null".equals(argText)) {
                otherArg = args[1];
            } else {
                otherArg = args[0];
            }
            callString = qualifierText + getAssertString(argText) + '(' +
                    otherArg.getText() + ')';
        } else {
            @NonNls final String argText = args[1].getText();
            final PsiExpression otherArg;
            if ("true".equals(argText) ||
                    "false".equals(argText) ||
                    "null".equals(argText)) {
                otherArg = args[2];
            } else {
                otherArg = args[1];
            }
            callString = qualifierText + getAssertString(argText) + '(' +
                    args[0].getText() + ", " + otherArg.getText() + ')';
        }
        replaceExpression(callString, call);
    }

    @NonNls
    private static String getAssertString(@NonNls String argText) {
        if ("true".equals(argText)) {
            return "assertTrue";
        }
        if ("false".equals(argText)) {
            return "assertFalse";
        }
        return "assertNull";
    }
}
