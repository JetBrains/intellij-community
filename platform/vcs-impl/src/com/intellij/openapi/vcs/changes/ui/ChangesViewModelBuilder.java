// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface ChangesViewModelBuilder {
  @NotNull
  ChangesViewModelBuilder insertChangeNode(@NotNull ChangesBrowserNode node);

  void insertFilesIntoNode(@NotNull Collection<? extends VirtualFile> files, @NotNull ChangesBrowserNode subtreeRoot);
}
