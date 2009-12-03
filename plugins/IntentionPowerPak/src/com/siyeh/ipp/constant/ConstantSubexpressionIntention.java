/*
 * Copyright 2003-2009 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.constant;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ConcatenationUtils;
import com.siyeh.ipp.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public class ConstantSubexpressionIntention extends MutablyNamedIntention {

    @Override
    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new ConstantSubexpressionPredicate();
    }

    @Override
    protected String getTextForElement(PsiElement element) {
        final PsiBinaryExpression binaryExpression =
                (PsiBinaryExpression)element.getParent();
        assert binaryExpression != null;
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression leftSide;
        if (lhs instanceof PsiBinaryExpression) {
            final PsiBinaryExpression lhsBinaryExpression =
                    (PsiBinaryExpression)lhs;
            leftSide = lhsBinaryExpression.getROperand();
        } else {
            leftSide = lhs;
        }
        final PsiJavaToken operationSign = binaryExpression.getOperationSign();
        final PsiExpression rhs = binaryExpression.getROperand();
        assert rhs != null;
        assert leftSide != null;
        return IntentionPowerPackBundle.message(
                "constant.subexpression.intention.name", leftSide.getText() +
                ' ' + operationSign.getText() + ' ' + rhs.getText());
    }

    @Override
    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiExpression expression = (PsiExpression)element.getParent();
        assert expression != null;
        String newExpression = "";
        final Object constantValue;
        if (expression instanceof PsiBinaryExpression) {
            final PsiBinaryExpression copy =
                    (PsiBinaryExpression)expression.copy();
            final PsiExpression lhs = copy.getLOperand();
            if (lhs instanceof PsiBinaryExpression) {
                final PsiBinaryExpression lhsBinaryExpression =
                        (PsiBinaryExpression)lhs;
                newExpression += getLeftSideText(lhsBinaryExpression);
                final PsiExpression rightSide =
                        lhsBinaryExpression.getROperand();
                assert rightSide != null;
                lhs.replace(rightSide);
            }
            if (ConcatenationUtils.isConcatenation(expression)) {
                constantValue = computeConstantStringExpression(copy);
            } else {
                constantValue =
                        ExpressionUtils.computeConstantExpression(copy);
            }
        } else {
            constantValue =
                    ExpressionUtils.computeConstantExpression(expression);
        }
        if (constantValue instanceof String) {
            newExpression += '"' + StringUtil.escapeStringCharacters(
                    constantValue.toString()) + '"';
        } else if (constantValue != null) {
            if (constantValue instanceof Number) {
                final Number number = (Number) constantValue;
                if (0 > number.doubleValue()) {
                    newExpression += " ";
                }
            }
            newExpression += constantValue.toString();
        }
        replaceExpression(newExpression, expression);
    }

    /**
     * handles the specified expression as if it was part of a string expression
     * (even if it's of another type) and computes a constant string expression
     * from it.
     */
    private static String computeConstantStringExpression(
            PsiBinaryExpression expression) {
        final PsiExpression lhs = expression.getLOperand();
        final String lhsText = lhs.getText();
        String result;
        if (lhsText.charAt(0) == '\'' || lhsText.charAt(0) == '"') {
            result = lhsText.substring(1, lhsText.length() - 1);
        } else {
            result = lhsText;
        }
        final PsiExpression rhs = expression.getROperand();
        assert rhs != null;
        final String rhsText = rhs.getText();
        if (rhsText.charAt(0) == '\'' || rhsText.charAt(0) == '"') {
            result += rhsText.substring(1, rhsText.length() - 1);
        } else {
            result += rhsText;
        }
        return result;
    }

    private static String getLeftSideText(
            PsiBinaryExpression binaryExpression) {
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiJavaToken sign = binaryExpression.getOperationSign();
        return lhs.getText() + sign.getText();
    }
}