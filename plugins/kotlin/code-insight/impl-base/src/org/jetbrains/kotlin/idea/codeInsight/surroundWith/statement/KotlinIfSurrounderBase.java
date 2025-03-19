// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement;


import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.MoveDeclarationsOutHelperKt;
import org.jetbrains.kotlin.psi.KtBlockExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtIfExpression;
import org.jetbrains.kotlin.psi.KtPsiFactory;

import static org.jetbrains.kotlin.idea.codeInsight.surroundWith.SurroundWithUtilKt.addStatementsInBlock;

public abstract class KotlinIfSurrounderBase extends KotlinStatementsSurrounder {

    @Override
    protected boolean isApplicableWhenUsedAsExpression() {
        return false;
    }

    @Override
    protected void surroundStatements(
            @NotNull ActionContext context,
            @NotNull PsiElement container,
            @NotNull PsiElement @NotNull [] statements,
            @NotNull ModPsiUpdater updater
    ) {
        statements = MoveDeclarationsOutHelperKt.move(container, statements, isGenerateDefaultInitializers());

        if (statements.length == 0) return;

        KtIfExpression ifExpression = (KtIfExpression) new KtPsiFactory(context.project()).createExpression(getCodeTemplate());
        ifExpression = (KtIfExpression) container.addAfter(ifExpression, statements[statements.length - 1]);

        // TODO move a comment for first statement

        KtBlockExpression thenBranch = (KtBlockExpression) ifExpression.getThen();
        assert thenBranch != null : "Then branch should exist for created if expression: " + ifExpression.getText();
        // Add statements in then branch of created if
        addStatementsInBlock(thenBranch, statements);

        // Delete statements from original code
        container.deleteChildRange(statements[0], statements[statements.length - 1]);
        applyNavigationAndDropCondition(updater, ifExpression);
    }

    public static void applyNavigationAndDropCondition(@NotNull ModPsiUpdater updater, KtIfExpression ifExpression) {
        KtExpression condition = ifExpression.getCondition();
        assert condition != null : "Condition should exists for created if expression: " + ifExpression.getText();
        // Delete condition from created if
        updater.select(TextRange.from(condition.getTextOffset(), 0));
        condition.delete();
    }

    protected abstract @NotNull String getCodeTemplate();

    protected abstract boolean isGenerateDefaultInitializers();
}
