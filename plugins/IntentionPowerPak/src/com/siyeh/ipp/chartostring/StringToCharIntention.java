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
package com.siyeh.ipp.chartostring;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class StringToCharIntention extends Intention {

    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new StringToCharPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        final PsiLiteralExpression stringLiteral =
                (PsiLiteralExpression)element;
        final String stringLiteralText = stringLiteral.getText();
        final String charLiteral = charForStringLiteral(stringLiteralText);
        replaceExpression(charLiteral, stringLiteral);
    }

    private static String charForStringLiteral(String stringLiteral) {
        if ("\"'\"".equals(stringLiteral)) {
            return "'\\''";
        } else {
            return '\'' +
                    stringLiteral.substring(1, stringLiteral.length() - 1) +
                    '\'';
        }
    }
}