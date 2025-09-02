// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Implementing it will not make the refactoring itself previewable.
 * We don't have the concept of intention preview on refactorings normally.
 * Refactorings may have a different kind of preview, which displays a toolwindow showing which references are to be updated.
 * <p>
 * Implementing {@link PreviewableRefactoringActionHandler} will simplify
 * generating intention preview for intention actions which delegate to this refactoring
 * (if any; there are refactorings that are never invoked via intention actions).
 */
public interface PreviewableRefactoringActionHandler extends RefactoringActionHandler {

  /**
   * @param project project
   * @param element start element to invoke the action on
   * @return an object that describes the action preview to display
   */
  default @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull PsiElement element) {
    return IntentionPreviewInfo.EMPTY;
  }
}
