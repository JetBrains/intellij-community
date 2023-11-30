// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement;


import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

    @Nullable
    @Override
    protected TextRange surroundStatements(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement container, PsiElement @NotNull [] statements) {
        statements = MoveDeclarationsOutHelperKt.move(container, statements, isGenerateDefaultInitializers());

        if (statements.length == 0) {
            return null;
        }

        KtIfExpression ifExpression = (KtIfExpression) new KtPsiFactory(project).createExpression(getCodeTemplate());
        ifExpression = (KtIfExpression) container.addAfter(ifExpression, statements[statements.length - 1]);

        // TODO move a comment for first statement

        KtBlockExpression thenBranch = (KtBlockExpression) ifExpression.getThen();
        assert thenBranch != null : "Then branch should exist for created if expression: " + ifExpression.getText();
        // Add statements in then branch of created if
        addStatementsInBlock(thenBranch, statements);

        // Delete statements from original code
        container.deleteChildRange(statements[0], statements[statements.length - 1]);

        ifExpression = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(ifExpression);
        if (ifExpression == null) {
            return null;
        }

        return getRange(editor, ifExpression);
    }

    @NotNull
    public static TextRange getRange(Editor editor, @NotNull KtIfExpression ifExpression) {
        KtExpression condition = ifExpression.getCondition();
        assert condition != null : "Condition should exists for created if expression: " + ifExpression.getText();
        // Delete condition from created if
        TextRange range = condition.getTextRange();
        TextRange textRange = new TextRange(range.getStartOffset(), range.getStartOffset());
        editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
        return textRange;
    }

    @NotNull
    protected abstract String getCodeTemplate();

    protected abstract boolean isGenerateDefaultInitializers();
}
