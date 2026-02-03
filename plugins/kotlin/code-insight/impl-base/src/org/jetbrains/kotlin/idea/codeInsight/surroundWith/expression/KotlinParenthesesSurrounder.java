// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.KotlinExpressionSurrounder;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtParenthesizedExpression;
import org.jetbrains.kotlin.psi.KtPsiFactory;

public class KotlinParenthesesSurrounder extends KotlinExpressionSurrounder {
    @SuppressWarnings("DialogTitleCapitalization")
    @Override
    public String getTemplateDescription() {
        return CodeInsightBundle.message("surround.with.parenthesis.template");
    }

    @Override
    protected void surroundExpression(@NotNull ActionContext context, @NotNull KtExpression expression, @NotNull ModPsiUpdater updater) {
        KtPsiFactory factory = new KtPsiFactory(context.project());
        KtParenthesizedExpression parenthesizedExpression =
                (KtParenthesizedExpression) factory.createExpression("(a)");
        KtExpression expressionWithoutParentheses = parenthesizedExpression.getExpression();
        assert expressionWithoutParentheses != null : "KtExpression should exists for " + parenthesizedExpression.getText() + " expression";
        expressionWithoutParentheses.replace(expression);

        expression = (KtExpression) expression.replace(parenthesizedExpression);

        int offset = expression.getTextRange().getEndOffset();
        updater.select(TextRange.from(offset, 0));
    }
}
