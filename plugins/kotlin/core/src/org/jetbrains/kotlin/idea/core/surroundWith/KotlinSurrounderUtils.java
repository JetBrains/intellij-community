// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.surroundWith;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class KotlinSurrounderUtils {

    private KotlinSurrounderUtils() {
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
}
