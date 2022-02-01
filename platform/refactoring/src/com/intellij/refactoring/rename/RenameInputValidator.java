// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * Validates input for new chosen name of the element to be renamed.
 * <p>
 * Extend {@link RenameInputValidatorEx} to provide custom error message.
 *
 * @author Gregory.Shrago
 */
public interface RenameInputValidator {
  ExtensionPointName<RenameInputValidator> EP_NAME = ExtensionPointName.create("com.intellij.renameInputValidator");

  @NotNull
  ElementPattern<? extends PsiElement> getPattern();

  /**
   * Invoked for elements accepted by pattern {@link #getPattern()}.
   * <p>
   * Return {@code true} if {@link RenameInputValidatorEx} should return custom error message,
   * otherwise default message "'[newName]' is not a valid identifier" will be shown.
   */
  boolean isInputValid(@NotNull final String newName, @NotNull final PsiElement element, @NotNull final ProcessingContext context);
}
