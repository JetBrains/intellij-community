// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.KotlinExpressionSurrounder;
import org.jetbrains.kotlin.psi.*;

public class KotlinStringTemplateSurrounder extends KotlinExpressionSurrounder {
    @Override
    public @NlsSafe String getTemplateDescription() {
        return "\"${expr}\"";
    }

    @Override
    public boolean isApplicable(@NotNull KtExpression expression) {
        return !(expression instanceof KtStringTemplateExpression) && super.isApplicable(expression);
    }

    @Override
    protected void surroundExpression(@NotNull ActionContext context, @NotNull KtExpression expression, @NotNull ModPsiUpdater updater) {
        KtPsiFactory factory = new KtPsiFactory(context.project());
        KtStringTemplateExpression stringTemplateExpression =
                (KtStringTemplateExpression) factory.createExpression(getCodeTemplate(expression));
        KtStringTemplateEntry templateEntry = stringTemplateExpression.getEntries()[0];
        KtExpression innerExpression = templateEntry.getExpression();
        assert innerExpression != null : "KtExpression should exists for " + stringTemplateExpression;
        innerExpression.replace(expression);

        expression = (KtExpression) expression.replace(stringTemplateExpression);

        int offset = expression.getTextRange().getEndOffset();
        updater.select(TextRange.from(offset, 0));
    }

    private static String getCodeTemplate(KtExpression expression) {
        if (expression.getChildren().length > 0 ||
            expression instanceof KtConstantExpression) {
            return "\"${a}\"";
        }
        return "\"$a\"";
    }
}
