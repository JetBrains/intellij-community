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
import com.siyeh.ipp.psiutils.BoolUtils;
import com.siyeh.ipp.psiutils.ComparisonUtils;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;

public class DemorgansIntention extends MutablyNamedIntention {

    protected String getTextForElement(PsiElement element) {
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression)element;
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        if (tokenType.equals(JavaTokenType.ANDAND)) {
            return IntentionPowerPackBundle.message("demorgans.intention.name1");
        } else {
            return IntentionPowerPackBundle.message("demorgans.intention.name2");
        }
    }

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ConjunctionPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        PsiBinaryExpression exp =
                (PsiBinaryExpression)element;
        final PsiJavaToken sign = exp.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        PsiElement parent = exp.getParent();
        while (isConjunctionExpression(parent, tokenType)) {
            exp = (PsiBinaryExpression)parent;
            assert exp != null;
            parent = exp.getParent();
        }
        final String newExpression =
                convertConjunctionExpression(exp, tokenType);
        replaceExpressionWithNegatedExpressionString(newExpression,
                exp);
    }

    private static String convertConjunctionExpression(PsiBinaryExpression exp,
                                                       IElementType tokenType) {
        final PsiExpression lhs = exp.getLOperand();
        final String lhsText;
        if (isConjunctionExpression(lhs, tokenType)) {
            lhsText = convertConjunctionExpression((PsiBinaryExpression)lhs,
                    tokenType);
        } else {
            lhsText = convertLeafExpression(lhs);
        }
        final PsiExpression rhs = exp.getROperand();
        final String rhsText;
        if (isConjunctionExpression(rhs, tokenType)) {
            rhsText = convertConjunctionExpression((PsiBinaryExpression)rhs,
                    tokenType);
        } else {
            rhsText = convertLeafExpression(rhs);
        }

        final String flippedConjunction;
        if (tokenType.equals(JavaTokenType.ANDAND)) {
            flippedConjunction = "||";
        } else {
            flippedConjunction = "&&";
        }

        return lhsText + flippedConjunction + rhsText;
    }

    private static String convertLeafExpression(PsiExpression condition) {
        if (BoolUtils.isNegation(condition)) {
            final PsiExpression negated = BoolUtils.getNegated(condition);
            if (negated == null) {
                return "";
            }
            if (ParenthesesUtils.getPrecendence(negated) >
                    ParenthesesUtils.OR_PRECEDENCE) {
                return '(' + negated.getText() + ')';
            }
            return negated.getText();
        } else if (ComparisonUtils.isComparison(condition)) {
            final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression)condition;
            final PsiJavaToken sign = binaryExpression.getOperationSign();
            final String negatedComparison =
                    ComparisonUtils.getNegatedComparison(sign);
            final PsiExpression lhs = binaryExpression.getLOperand();
            final PsiExpression rhs = binaryExpression.getROperand();
            assert rhs != null;
            return lhs.getText() + negatedComparison + rhs.getText();
        } else if (ParenthesesUtils.getPrecendence(condition) >
                ParenthesesUtils.PREFIX_PRECEDENCE) {
            return "!(" + condition.getText() + ')';
        } else {
            return '!' + condition.getText();
        }
    }

    private static boolean isConjunctionExpression(PsiElement exp,
                                                   IElementType conjunctionType) {
        if (!(exp instanceof PsiBinaryExpression)) {
            return false;
        }
        final PsiBinaryExpression binExp = (PsiBinaryExpression)exp;
        final PsiJavaToken sign = binExp.getOperationSign();
        final IElementType tokenType = sign.getTokenType();
        return tokenType.equals(conjunctionType);
    }
}