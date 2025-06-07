// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface VcsDirtyScopeBuilder {
  boolean belongsTo(@NotNull FilePath path);

  void addDirtyPathFast(@NotNull VirtualFile vcsRoot, @NotNull FilePath filePath, boolean recursively);

  default void markRootDirty(@NotNull VirtualFile vcsRoot) {
    addDirtyPathFast(vcsRoot, VcsUtil.getFilePath(vcsRoot), true);
  }

  void markEverythingDirty();

  @NotNull VcsModifiableDirtyScope pack();
}
