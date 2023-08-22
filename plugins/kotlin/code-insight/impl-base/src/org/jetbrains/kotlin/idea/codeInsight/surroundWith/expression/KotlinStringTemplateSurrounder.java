// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

    @Nullable
    @Override
    public TextRange surroundExpression(@NotNull Project project, @NotNull Editor editor, @NotNull KtExpression expression) {
        KtStringTemplateExpression stringTemplateExpression =
                (KtStringTemplateExpression) new KtPsiFactory(expression.getProject()).createExpression(getCodeTemplate(expression));
        KtStringTemplateEntry templateEntry = stringTemplateExpression.getEntries()[0];
        KtExpression innerExpression = templateEntry.getExpression();
        assert innerExpression != null : "KtExpression should exists for " + stringTemplateExpression;
        innerExpression.replace(expression);

        expression = (KtExpression) expression.replace(stringTemplateExpression);

        CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(expression);

        int offset = expression.getTextRange().getEndOffset();
        return new TextRange(offset, offset);
    }

    private String getCodeTemplate(KtExpression expression) {
        if (expression.getChildren().length > 0 ||
            expression instanceof KtConstantExpression) {
            return "\"${a}\"";
        }
        return "\"$a\"";
    }
}
