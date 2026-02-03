// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ModNavigator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.name.ClassId;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtFinallySection;
import org.jetbrains.kotlin.psi.KtTryExpression;

import java.util.List;

public class KotlinTryFinallySurrounder extends KotlinTrySurrounderBase<KtFinallySection> {

    @Override
    protected String getCodeTemplate(List<ClassId> exceptionClasses) {
        return "try { \n} finally {\n\n}";
    }

    @Override
    protected void applyNavigation(@NotNull ActionContext context, @NotNull ModPsiUpdater navigator, KtFinallySection element) {
        moveCaretToBlockCenter(context, navigator, element.getFinalExpression());
    }

    public static void moveCaretToBlockCenter(@NotNull ActionContext context, @NotNull ModNavigator navigator, KtElement expression) {
        Project project = context.project();
        Document document = expression.getContainingFile().getFileDocument();
        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
        TextRange finallyBlockRange = expression.getTextRange();
        int newLineOffset = finallyBlockRange.getStartOffset() + 2;
        int offset = CodeStyleManager.getInstance(project).adjustLineIndent(document, newLineOffset);
        navigator.select(TextRange.from(offset, 0));
        psiDocumentManager.commitDocument(document);
    }

    @Override
    protected KtFinallySection getSelectionElement(@NotNull KtTryExpression tryExpression) {
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(tryExpression.getProject());
        tryExpression = (KtTryExpression) codeStyleManager.reformat(tryExpression);
        return tryExpression.getFinallyBlock();
    }

    @SuppressWarnings("DialogTitleCapitalization")
    @Override
    public String getTemplateDescription() {
        return CodeInsightBundle.message("surround.with.try.finally.template");
    }
}
