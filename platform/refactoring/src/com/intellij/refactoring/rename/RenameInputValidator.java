// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * Validates input for new chosen name of the element to be renamed.
 * Extend {@link RenameInputValidatorEx} to provide custom error messages.
 * <p>
 * Register in {@code com.intellij.renameInputValidator} extension point.
 * @see RenameInputValidatorRegistry
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/rename-refactoring.html">Rename Refactoring (IntelliJ Platform Docs)</a>
 */
public interface RenameInputValidator {
  ExtensionPointName<RenameInputValidator> EP_NAME = ExtensionPointName.create("com.intellij.renameInputValidator");

  @NotNull
  ElementPattern<? extends PsiElement> getPattern();

  /**
   * Invoked for elements accepted by pattern returned from {@link #getPattern()}.
   * <p>
   * Return {@code true} if {@link RenameInputValidatorEx} should return a custom error message,
   * otherwise default message "'[newName]' is not a valid identifier" will be shown.
   */
  boolean isInputValid(final @NotNull String newName, final @NotNull PsiElement element, final @NotNull ProcessingContext context);
}
