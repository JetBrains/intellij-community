// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.surroundWith;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.caches.resolve.ResolutionUtils;
import org.jetbrains.kotlin.idea.core.util.KotlinIdeaCoreBundle;
import org.jetbrains.kotlin.psi.KtBlockExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.resolve.BindingContext;
import org.jetbrains.kotlin.resolve.bindingContextUtil.BindingContextUtilsKt;
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode;

public class KotlinSurrounderUtils {
    @NlsContexts.DialogTitle
    public static String SURROUND_WITH() {
        return KotlinIdeaCoreBundle.message("surround.with.title");
    }

    @NlsContexts.DialogMessage
    public static String SURROUND_WITH_ERROR() {
        return KotlinIdeaCoreBundle.message("surround.with.error.cannot.perform.action");
    }

    private KotlinSurrounderUtils() {
    }

    public static void addStatementsInBlock(
            @NotNull KtBlockExpression block,
            @NotNull PsiElement[] statements
    ) {
        PsiElement lBrace = block.getFirstChild();
        block.addRangeAfter(statements[0], statements[statements.length - 1], lBrace);
    }

    public static void showErrorHint(@NotNull Project project, @NotNull Editor editor, @NlsContexts.DialogMessage @NotNull String message) {
        showErrorHint(project, editor, message, SURROUND_WITH(), null);
    }

    public static void showErrorHint(
            @NotNull Project project,
            @NotNull Editor editor,
            @NlsContexts.DialogMessage @NotNull String message,
            @NlsContexts.DialogTitle @NotNull String title,
            @Nullable String helpId
    ) {
        if (ApplicationManager.getApplication().isUnitTestMode()) throw new CommonRefactoringUtil.RefactoringErrorHintException(message);
        CommonRefactoringUtil.showErrorHint(project, editor, message, title, helpId);
    }

    public static boolean isUsedAsStatement(@NotNull KtExpression expression) {
        BindingContext context = ResolutionUtils.analyze(expression, BodyResolveMode.PARTIAL_WITH_CFA);
        return BindingContextUtilsKt.isUsedAsStatement(expression, context);
    }

    public static boolean isUsedAsExpression(@NotNull KtExpression expression) {
        BindingContext context = ResolutionUtils.analyze(expression, BodyResolveMode.PARTIAL_WITH_CFA);
        return BindingContextUtilsKt.isUsedAsExpression(expression, context);
    }
}
