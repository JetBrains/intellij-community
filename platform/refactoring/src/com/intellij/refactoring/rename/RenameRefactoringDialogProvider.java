// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class RenameRefactoringDialogProvider {
  public static final ExtensionPointName<RenameRefactoringDialogProvider>
    EP_NAME = ExtensionPointName.create("com.intellij.renameRefactoringDialogProvider");

  public boolean isApplicable(RenamePsiElementProcessorBase processor) {
    return false;
  }

  public abstract RenameRefactoringDialog createDialog(@NotNull Project project,
                                                       @NotNull PsiElement element,
                                                       @Nullable PsiElement nameSuggestionContext,
                                                       @Nullable Editor editor);
}
