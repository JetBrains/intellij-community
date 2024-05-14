// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiNavigator;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.MoveDeclarationsOutHelperKt;
import org.jetbrains.kotlin.psi.*;

import static org.jetbrains.kotlin.idea.codeInsight.surroundWith.SurroundWithUtilKt.addStatementsInBlock;

public abstract class KotlinTrySurrounderBase<SELECTION extends KtElement> extends KotlinStatementsSurrounder {

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
        statements = MoveDeclarationsOutHelperKt.move(container, statements, true);
        if (statements.length == 0) return;

        Project project = context.project();
        KtTryExpression tryExpression = (KtTryExpression) new KtPsiFactory(project).createExpression(getCodeTemplate());
        tryExpression = (KtTryExpression) container.addAfter(tryExpression, statements[statements.length - 1]);

        // TODO move a comment for first statement

        KtBlockExpression tryBlock = tryExpression.getTryBlock();
        // Add statements in try block of created try - catch - finally
        addStatementsInBlock(tryBlock, statements);

        // Delete statements from original code
        container.deleteChildRange(statements[0], statements[statements.length - 1]);

        applyNavigation(context, updater, getSelectionElement(tryExpression));
    }

    protected void applyNavigation(@NotNull ActionContext context, @NotNull ModPsiNavigator navigator, SELECTION element) {
        if (element != null) {
            navigator.select(element);
        }
    }

    protected abstract String getCodeTemplate();

    protected abstract SELECTION getSelectionElement(@NotNull KtTryExpression expression);

    public static KtElement getCatchTypeParameter(@NotNull KtTryExpression expression) {
        KtParameter parameter = expression.getCatchClauses().get(0).getCatchParameter();
        assert parameter != null : "Catch parameter should exists for " + expression.getText();
        KtElement typeReference = parameter.getTypeReference();
        assert typeReference != null : "Type reference for parameter should exists for " + expression.getText();
        return typeReference;
    }

}
