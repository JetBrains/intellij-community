// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface VcsLogModifiableIndex extends VcsLogIndex {

  void scheduleIndex(boolean full);

  void markForIndexing(int commit, @NotNull VirtualFile root);

  void reindexWithRenames(int commit, @NotNull VirtualFile root);

  void markCorrupted();
}
