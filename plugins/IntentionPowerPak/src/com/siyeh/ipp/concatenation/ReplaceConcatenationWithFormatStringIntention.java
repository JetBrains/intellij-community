/*
 * Copyright 2008-2011 Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ConcatenationUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ReplaceConcatenationWithFormatStringIntention
        extends Intention {

    @Override
    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new Jdk5StringConcatenationPredicate();
    }

    @Override
    protected void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        PsiBinaryExpression expression =
                (PsiBinaryExpression)element;
        PsiElement parent = expression.getParent();
        while (ConcatenationUtils.isConcatenation(parent)) {
            expression = (PsiBinaryExpression)parent;
            if (expression == null) {
                return;
            }
            parent = expression.getParent();
        }

        final StringBuilder formatString = new StringBuilder();
        final List<PsiExpression> formatParameters = new ArrayList();
        buildFormatString(expression, formatString, formatParameters);
        if (replaceWithPrintfExpression(expression, formatString,
                formatParameters)) {
            return;
        }
        final StringBuilder newExpression = new StringBuilder();
        newExpression.append("java.lang.String.format(\"");
        newExpression.append(formatString);
        newExpression.append('\"');
        for (PsiExpression formatParameter : formatParameters) {
            newExpression.append(", ");
            newExpression.append(formatParameter.getText());
        }
        newExpression.append(')');
        replaceExpression(newExpression.toString(), expression);
    }

    private static boolean replaceWithPrintfExpression(
            PsiBinaryExpression expression,
            CharSequence formatString,
            List<PsiExpression> formatParameters)
            throws IncorrectOperationException {
        final PsiElement expressionParent = expression.getParent();
        if (!(expressionParent instanceof PsiExpressionList)) {
            return false;
        }
        final PsiElement grandParent = expressionParent.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression)) {
            return false;
        }
        final PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression) grandParent;
        final PsiReferenceExpression methodExpression =
                methodCallExpression.getMethodExpression();
        final String name = methodExpression.getReferenceName();
        final boolean insertNewline;
        if ("println".equals(name)) {
            insertNewline = true;
        } else if ("print".equals(name)) {
            insertNewline = false;
        } else {
            return false;
        }
        final PsiMethod method = methodCallExpression.resolveMethod();
        if (method == null) {
            return false;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return false;
        }
        final String qualifiedName = containingClass.getQualifiedName();
        if (!"java.io.PrintStream".equals(qualifiedName) &&
                !"java.io.Printwriter".equals(qualifiedName)) {
            return false;
        }
        final StringBuilder newExpression = new StringBuilder();
        final PsiExpression qualifier =
                methodExpression.getQualifierExpression();
        if (qualifier != null) {
            newExpression.append(qualifier.getText());
            newExpression.append('.');
        }
        newExpression.append("printf(\"");
        newExpression.append(formatString);
        if (insertNewline) {
            newExpression.append("%n");
        }
        newExpression.append('\"');
        for (PsiExpression formatParameter : formatParameters) {
            newExpression.append(", ");
            newExpression.append(formatParameter.getText());
        }
        newExpression.append(')');
        replaceExpression(newExpression.toString(), methodCallExpression);
        return true;
    }

    private static void buildFormatString(
            PsiExpression expression, StringBuilder formatString,
            List<PsiExpression> formatParameters) {
        if (expression instanceof PsiLiteralExpression) {
            final PsiLiteralExpression literalExpression =
                    (PsiLiteralExpression) expression;
            final Object value = literalExpression.getValue();
            final String text =
                    String.valueOf(value).replace("%", "%%").replace(
                            "\\'", "'");
            formatString.append(text);
        } else if (expression instanceof PsiBinaryExpression) {
            final PsiType type = expression.getType();
            if (type != null && type.equalsToText("java.lang.String")) {
                final PsiBinaryExpression binaryExpression =
                    (PsiBinaryExpression) expression;
                final PsiExpression lhs = binaryExpression.getLOperand();
                buildFormatString(lhs, formatString, formatParameters);
                final PsiExpression rhs = binaryExpression.getROperand();
                if (rhs != null) {
                    buildFormatString(rhs, formatString, formatParameters);
                }
            } else {
                addFormatParameter(expression, formatString, formatParameters);
            }
        } else {
          addFormatParameter(expression, formatString, formatParameters);
        }
    }

  private static void addFormatParameter(PsiExpression expression,
                                         StringBuilder formatString,
                                         List<PsiExpression> formatParameters) {
    final PsiType type = expression.getType();
    if (type != null &&
            (type.equalsToText("long") ||
            type.equalsToText("int") ||
            type.equalsToText("java.lang.Long") ||
            type.equalsToText("java.lang.Integer"))) {
        formatString.append("%d");
    } else {
        formatString.append("%s");
    }
    formatParameters.add(expression);
  }
}
