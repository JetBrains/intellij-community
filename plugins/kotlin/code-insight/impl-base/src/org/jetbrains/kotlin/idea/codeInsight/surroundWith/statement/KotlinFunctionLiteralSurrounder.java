// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.MoveDeclarationsOutHelperKt;
import org.jetbrains.kotlin.psi.*;

import static org.jetbrains.kotlin.idea.codeInsight.surroundWith.SurroundWithUtilKt.addStatementsInBlock;

public class KotlinFunctionLiteralSurrounder extends KotlinStatementsSurrounder {

    @Override
    protected void surroundStatements(
            @NotNull ActionContext context,
            @NotNull PsiElement container,
            @NotNull PsiElement @NotNull [] statements,
            @NotNull ModPsiUpdater updater
    ) {
        statements = MoveDeclarationsOutHelperKt.move(container, statements, true);

        if (statements.length == 0) return;

        KtPsiFactory psiFactory = new KtPsiFactory(context.project());
        KtCallExpression callExpression = (KtCallExpression) psiFactory.createExpression("run {\n}");
        callExpression = (KtCallExpression) container.addAfter(callExpression, statements[statements.length - 1]);
        container.addBefore(psiFactory.createWhiteSpace(), callExpression);

        KtLambdaExpression bodyExpression = callExpression.getLambdaArguments().get(0).getLambdaExpression();
        assert bodyExpression != null : "Body expression should exists for " + callExpression.getText();
        KtBlockExpression blockExpression = bodyExpression.getBodyExpression();
        assert blockExpression != null : "KtBlockExpression should exists for " + callExpression.getText();
        //Add statements in function literal block
        addStatementsInBlock(blockExpression, statements);

        //Delete statements from original code
        container.deleteChildRange(statements[0], statements[statements.length - 1]);
        assert callExpression != null;
        KtExpression literalName = callExpression.getCalleeExpression();
        assert literalName != null : "Run expression should have callee expression " + callExpression.getText();
        updater.select(literalName);
    }

    @Override
    public String getTemplateDescription() {
        //noinspection DialogTitleCapitalization,HardCodedStringLiteral
        return "run { }";
    }
}
