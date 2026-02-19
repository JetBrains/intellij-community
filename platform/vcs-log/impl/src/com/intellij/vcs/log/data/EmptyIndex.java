// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.data.index.IndexDataGetter;
import com.intellij.vcs.log.data.index.VcsLogModifiableIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

public class EmptyIndex implements VcsLogModifiableIndex {
  @Override
  public void scheduleIndex(boolean full) {
  }

  @Override
  public boolean isIndexed(int commit) {
    return false;
  }

  @Override
  public boolean isIndexed(@NotNull VirtualFile root) {
    return false;
  }

  @Override
  public boolean isIndexingEnabled(@NotNull VirtualFile root) {
    return false;
  }

  @Override
  public void markForIndexing(int commit, @NotNull VirtualFile root) {
  }

  @Override
  public @Nullable IndexDataGetter getDataGetter() {
    return null;
  }

  @Override
  public @NotNull Set<VirtualFile> getIndexingRoots() {
    return Collections.emptySet();
  }

  @Override
  public void addListener(@NotNull IndexingFinishedListener l) {
  }

  @Override
  public void removeListener(@NotNull IndexingFinishedListener l) {
  }

  @Override
  public void markCorrupted() {
  }
}
