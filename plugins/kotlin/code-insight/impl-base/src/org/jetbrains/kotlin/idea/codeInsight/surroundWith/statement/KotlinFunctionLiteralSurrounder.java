// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.codeInsight.surroundWith.MoveDeclarationsOutHelperKt;
import org.jetbrains.kotlin.psi.*;

import static org.jetbrains.kotlin.idea.codeInsight.surroundWith.SurroundWithUtilKt.addStatementsInBlock;

public class KotlinFunctionLiteralSurrounder extends KotlinStatementsSurrounder {
    @Nullable
    @Override
    protected TextRange surroundStatements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement container, PsiElement @NotNull [] statements) {
        statements = MoveDeclarationsOutHelperKt.move(container, statements, true);

        if (statements.length == 0) {
            return null;
        }

        KtPsiFactory psiFactory = new KtPsiFactory(project);
        KtCallExpression callExpression = (KtCallExpression) psiFactory.createExpression("run {\n}");
        callExpression = (KtCallExpression) container.addAfter(callExpression, statements[statements.length - 1]);
        container.addBefore(psiFactory.createWhiteSpace(), callExpression);

        KtLambdaExpression bodyExpression = callExpression.getLambdaArguments().get(0).getLambdaExpression();
        assert bodyExpression != null : "Body expression should exists for " + callExpression.getText();
        KtBlockExpression blockExpression = bodyExpression.getBodyExpression();
        assert blockExpression != null : "JetBlockExpression should exists for " + callExpression.getText();
        //Add statements in function literal block
        addStatementsInBlock(blockExpression, statements);

        //Delete statements from original code
        container.deleteChildRange(statements[0], statements[statements.length - 1]);

        callExpression = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(callExpression);

        assert callExpression != null;
        KtExpression literalName = callExpression.getCalleeExpression();
        assert literalName != null : "Run expression should have callee expression " + callExpression.getText();
        return literalName.getTextRange();
    }

    @Override
    public String getTemplateDescription() {
        //noinspection DialogTitleCapitalization,HardCodedStringLiteral
        return "run { }";
    }
}
