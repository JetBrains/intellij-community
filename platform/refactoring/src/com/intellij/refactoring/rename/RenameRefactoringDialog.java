// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import org.jetbrains.annotations.NotNull;

public interface RenameRefactoringDialog {
  String[] getSuggestedNames();
  void performRename(@NotNull String newName);

  void show();

  void close();
}
