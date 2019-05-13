// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface IgnoredFilesHolder extends FileHolder {
  void addFile(@NotNull VirtualFile file);

  boolean containsFile(@NotNull VirtualFile file);

  @NotNull
  Collection<VirtualFile> values();

  @Override
  void cleanAndAdjustScope(@NotNull VcsModifiableDirtyScope scope);
}
