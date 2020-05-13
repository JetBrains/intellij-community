// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface ChangesViewModelBuilder {
  @NotNull
  ChangesViewModelBuilder insertSubtreeRoot(@NotNull ChangesBrowserNode<?> node);

  void insertFilesIntoNode(@NotNull Collection<? extends VirtualFile> files, @NotNull ChangesBrowserNode<?> subtreeRoot);

  void insertChanges(@NotNull Collection<? extends Change> changes, @NotNull ChangesBrowserNode<?> subtreeRoot);
}
