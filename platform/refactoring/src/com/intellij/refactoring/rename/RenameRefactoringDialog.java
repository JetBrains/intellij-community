// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface RenameRefactoringDialog {
  String[] getSuggestedNames();

  /**
   * Add an explicit set of suggested names in addition to submitted by providers.
   * Implementations may ignore this method or suggest them to the user.
   * 
   * @param names names to add
   */
  default void addSuggestedNames(@NotNull Collection<@NotNull String> names) {
    
  }
  
  void performRename(@NotNull String newName);

  void show();

  void close();
}
