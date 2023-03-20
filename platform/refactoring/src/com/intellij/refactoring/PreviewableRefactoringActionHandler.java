// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

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
