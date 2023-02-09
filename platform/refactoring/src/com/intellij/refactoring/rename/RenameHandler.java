// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Allows customizing UI/workflow of "Rename" refactoring.
 */
public interface RenameHandler extends RefactoringActionHandler {
  ExtensionPointName<RenameHandler> EP_NAME = new ExtensionPointName<>("com.intellij.renameHandler");

  /**
   * Called during rename action update, should not perform any user interactions.
   */
  boolean isAvailableOnDataContext(@NotNull DataContext dataContext);

  /**
   * Called on rename actionPerformed. Can obtain additional info from user.
   */
  default boolean isRenaming(@NotNull DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }
}