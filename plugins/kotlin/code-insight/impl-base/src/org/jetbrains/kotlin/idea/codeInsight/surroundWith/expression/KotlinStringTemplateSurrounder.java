// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.KotlinExpressionSurrounder;
import org.jetbrains.kotlin.psi.*;

public class KotlinStringTemplateSurrounder extends KotlinExpressionSurrounder {
    @NlsSafe
    @Override
    public String getTemplateDescription() {
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

        CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(expression);

        int offset = expression.getTextRange().getEndOffset();
        updater.moveCaretTo(offset);
    }

    private static String getCodeTemplate(KtExpression expression) {
        if (expression.getChildren().length > 0 ||
            expression instanceof KtConstantExpression) {
            return "\"${a}\"";
        }
        return "\"$a\"";
    }
}
