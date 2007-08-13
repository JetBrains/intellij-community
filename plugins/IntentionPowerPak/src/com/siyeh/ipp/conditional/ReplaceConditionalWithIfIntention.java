/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.conditional;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceConditionalWithIfIntention extends Intention {

    @NotNull
    public PsiElementPredicate getElementPredicate() {
        return new ReplaceConditionalWithIfPredicate();
    }

    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiConditionalExpression expression =
                (PsiConditionalExpression)element;
        replaceConditionalWithIf(expression);
    }

    private static void replaceConditionalWithIf(
            PsiConditionalExpression expression)
            throws IncorrectOperationException {
        final PsiStatement statement =
                PsiTreeUtil.getParentOfType(expression, PsiStatement.class);
        if (statement == null) {
            return;
        }
        final PsiVariable variable;
        if (statement instanceof PsiDeclarationStatement) {
            variable =
                    PsiTreeUtil.getParentOfType(expression, PsiVariable.class);
        } else {
            variable = null;
        }
        final PsiExpression thenExpression = expression.getThenExpression();
        final String thenExpressionText;
        if (thenExpression != null) {
            thenExpressionText = thenExpression.getText();
        } else {
            thenExpressionText = "";
        }
        final PsiExpression elseExpression = expression.getElseExpression();
        final String elseExpressionText;
        if (elseExpression != null) {
            elseExpressionText = elseExpression.getText();
        } else {
            elseExpressionText = "";
        }
        final PsiExpression condition = expression.getCondition();
        final PsiExpression strippedCondition =
                ParenthesesUtils.stripParentheses(condition);
        final StringBuilder newStatement = new StringBuilder();
        newStatement.append("if(");
        newStatement.append(strippedCondition.getText());
        newStatement.append(')');
        if (variable != null) {
            final String name = variable.getName();
            newStatement.append(name);
            newStatement.append('=');
            final PsiExpression initializer = variable.getInitializer();
            if (initializer == null) {
                return;
            }
            appendElementText(initializer, expression, thenExpressionText,
                    newStatement);
            newStatement.append("; else ");
            newStatement.append(name);
            newStatement.append('=');
            appendElementText(initializer, expression, elseExpressionText,
                    newStatement);
            newStatement.append(';');
            initializer.delete();
            final PsiManager manager = statement.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiStatement ifStatement = factory.createStatementFromText(
                    newStatement.toString(), statement);
            final PsiElement parent = statement.getParent();
            final PsiElement addedElement = parent.addAfter(ifStatement,
                    statement);
            final CodeStyleManager styleManager = manager.getCodeStyleManager();
            styleManager.reformat(addedElement);
        } else {
            appendElementText(statement, expression, thenExpressionText,
                    newStatement);
            newStatement.append(" else ");
            appendElementText(statement, expression, elseExpressionText,
                    newStatement);
            replaceStatement(newStatement.toString(), statement);
        }
    }

    private static void appendElementText(
            @NotNull PsiElement element,
            @Nullable PsiElement elementToReplace,
            @Nullable String replacement,
            @NotNull StringBuilder out) {
        if (element.equals(elementToReplace)) {
            out.append(replacement);
            return;
        }
        final PsiElement[] children = element.getChildren();
        if (children.length == 0) {
            out.append(element.getText());
            return;
        }
        for (PsiElement child : children) {
            appendElementText(child, elementToReplace, replacement, out);
        }
    }
}