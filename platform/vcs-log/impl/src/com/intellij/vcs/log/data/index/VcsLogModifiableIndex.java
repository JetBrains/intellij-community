// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface VcsLogModifiableIndex extends VcsLogIndex {

  void scheduleIndex(boolean full);

  void markForIndexing(int commit, @NotNull VirtualFile root);

  void markCorrupted();
}
