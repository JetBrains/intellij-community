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
package com.siyeh.ipp.bool;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;

public class FlipConjunctionIntention extends MutablyNamedIntention {

    protected String getTextForElement(PsiElement element) {
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression)element;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        return IntentionPowerPackBundle.message("flip.smth.intention.name",
                sign.getText());
    }

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ConjunctionPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        PsiExpression exp = (PsiExpression)element;
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)exp;
        assert binaryExpression != null;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType conjunctionType = sign.getTokenType();
        PsiElement parent = exp.getParent();
        while (isConjunctionExpression(parent, conjunctionType)) {
            exp = (PsiExpression)parent;
            assert exp != null;
            parent = exp.getParent();
        }
        final String newExpression = flipExpression(exp, conjunctionType);
        replaceExpression(newExpression, exp);
    }

    private static String flipExpression(PsiExpression expression,
                                         IElementType conjunctionType) {
        if (isConjunctionExpression(expression, conjunctionType)) {
            final PsiBinaryExpression andExpression =
                    (PsiBinaryExpression)expression;
            final PsiExpression rhs = andExpression.getROperand();
            final PsiExpression lhs = andExpression.getLOperand();
            final String conjunctionSign;
            if (conjunctionType.equals(JavaTokenType.ANDAND)) {
                conjunctionSign = "&&";
            } else {
                conjunctionSign = "||";
            }
            return flipExpression(rhs, conjunctionType) + ' ' +
                    conjunctionSign + ' ' +
                    flipExpression(lhs, conjunctionType);
        } else {
            return expression.getText();
        }
    }

    private static boolean isConjunctionExpression(
            PsiElement element, IElementType conjunctionType) {
        if (!(element instanceof PsiBinaryExpression)) {
            return false;
        }
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression)element;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        return tokenType.equals(conjunctionType);
    }
}