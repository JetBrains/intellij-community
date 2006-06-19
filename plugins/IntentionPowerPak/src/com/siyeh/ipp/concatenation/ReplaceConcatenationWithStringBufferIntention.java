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
package com.siyeh.ipp.concatenation;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class ReplaceConcatenationWithStringBufferIntention extends Intention {

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ReplaceConcatenationWithStringBufferPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException {
        PsiBinaryExpression expression =
                (PsiBinaryExpression)element;
        PsiElement parent = expression.getParent();
        while (ConcatenationUtils.isConcatenation(parent)) {
            expression = (PsiBinaryExpression)parent;
            assert expression != null;
            parent = expression.getParent();
        }
        final String text = expression.getText();
        @NonNls final StringBuilder newExpression =
                new StringBuilder(text.length() * 3);
        if (isPartOfStringBufferAppend(expression)) {
            assert parent != null;
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression)parent.getParent();
            assert methodCallExpression != null;
            final PsiReferenceExpression methodExpression =
                    methodCallExpression.getMethodExpression();
            final PsiExpression qualifierExpression =
                    methodExpression.getQualifierExpression();
            if (qualifierExpression != null) {
                final String qualifierText = qualifierExpression.getText();
                newExpression.append(qualifierText);
            }
            turnExpressionIntoChainedAppends(expression, newExpression);
            replaceExpression(newExpression.toString(), methodCallExpression);
        } else {
            final LanguageLevel languageLevel =
                    PsiUtil.getLanguageLevel(expression);
            if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) {
                newExpression.append("new StringBuffer()");
            } else {
                newExpression.append("new StringBuilder()");
            }
            turnExpressionIntoChainedAppends(expression, newExpression);
            newExpression.append(".toString()");
            replaceExpression(newExpression.toString(), expression);
        }
    }

    private static boolean isPartOfStringBufferAppend(
            PsiExpression expression) {
        PsiElement parent = expression.getParent();
        if (!(parent instanceof PsiExpressionList)) {
            return false;
        }
        parent = parent.getParent();
        if (!(parent instanceof PsiMethodCallExpression)) {
            return false;
        }
        final PsiMethodCallExpression methodCall =
                (PsiMethodCallExpression)parent;
        final PsiReferenceExpression methodExpression =
                methodCall.getMethodExpression();
        final PsiType type = methodExpression.getType();
        if (type == null) {
            return false;
        }
        final String className = type.getCanonicalText();
        if (!"java.lang.StringBuffer".equals(className) &&
                !"java.lang.StringBuilder".equals(className)) {
            return false;
        }
        @NonNls final String methodName = methodExpression.getReferenceName();
        return "append".equals(methodName);
    }

    private static void turnExpressionIntoChainedAppends(
            PsiExpression expression, @NonNls StringBuilder result) {
        if (ConcatenationUtils.isConcatenation(expression)) {
            final PsiBinaryExpression concat = (PsiBinaryExpression)expression;
            final PsiExpression lhs = concat.getLOperand();
            turnExpressionIntoChainedAppends(lhs, result);
            final PsiExpression rhs = concat.getROperand();
            turnExpressionIntoChainedAppends(rhs, result);
        } else {
            final PsiExpression strippedExpression =
                    ParenthesesUtils.stripParentheses(expression);
            result.append(".append(");
            if (strippedExpression != null) {
                result.append(strippedExpression.getText());
            }
            result.append(')');
        }
    }
}
