// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface IgnoredFilesHolder extends FileHolder {
  @Deprecated
  void addFile(@NotNull VirtualFile file);

  void addFile(@NotNull FilePath file);

  @Deprecated
  boolean containsFile(@NotNull VirtualFile file);

  boolean containsFile(@NotNull FilePath file);

  @Deprecated
  @NotNull
  Collection<VirtualFile> values();

  @NotNull
  Collection<FilePath> paths();

  @Override
  void cleanAndAdjustScope(@NotNull VcsModifiableDirtyScope scope);
}
