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

public abstract class KotlinTrySurrounderBase extends KotlinStatementsSurrounder {

    @Override
    protected boolean isApplicableWhenUsedAsExpression() {
        return false;
    }

    @Nullable
    @Override
    protected TextRange surroundStatements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement container,
            PsiElement @NotNull [] statements)
    {
        statements = MoveDeclarationsOutHelperKt.move(container, statements, true);

        if (statements.length == 0) {
            return null;
        }

        KtTryExpression tryExpression = (KtTryExpression) new KtPsiFactory(project).createExpression(getCodeTemplate());
        tryExpression = (KtTryExpression) container.addAfter(tryExpression, statements[statements.length - 1]);

        // TODO move a comment for first statement

        KtBlockExpression tryBlock = tryExpression.getTryBlock();
        // Add statements in try block of created try - catch - finally
        addStatementsInBlock(tryBlock, statements);

        // Delete statements from original code
        container.deleteChildRange(statements[0], statements[statements.length - 1]);

        tryExpression = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(tryExpression);
        if (tryExpression == null) return null;

        return getTextRangeForCaret(tryExpression);
    }

    protected abstract String getCodeTemplate();

    @NotNull
    protected abstract TextRange getTextRangeForCaret(@NotNull KtTryExpression expression);

    public static TextRange getCatchTypeParameterTextRange(@NotNull KtTryExpression expression) {
        KtParameter parameter = expression.getCatchClauses().get(0).getCatchParameter();
        assert parameter != null : "Catch parameter should exists for " + expression.getText();
        KtElement typeReference = parameter.getTypeReference();
        assert typeReference != null : "Type reference for parameter should exists for " + expression.getText();
        return typeReference.getTextRange();
    }

}
