// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Rename input validator with the ability to provide custom error messages.
 */
public interface RenameInputValidatorEx extends RenameInputValidator {

  /**
   * Called only if all {@link RenameInputValidator}s accept
   * the new name in {@link #isInputValid(String, PsiElement, ProcessingContext)}
   * and name is a valid identifier for the language of the element.
   *
   * @return {@code null} if {@code newName} is a valid name, custom error message otherwise
   */
  @Nullable
  @DialogMessage String getErrorMessage(@NotNull String newName, @NotNull Project project);
}
