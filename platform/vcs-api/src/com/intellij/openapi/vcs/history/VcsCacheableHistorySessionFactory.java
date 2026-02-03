// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.List;

public interface VcsCacheableHistorySessionFactory<Cacheable extends Serializable, T extends VcsAbstractHistorySession> {
  T createFromCachedData(@Nullable Cacheable cacheable,
                         @NotNull List<? extends VcsFileRevision> revisions,
                         @NotNull FilePath filePath,
                         @Nullable VcsRevisionNumber currentRevision);

  /**
   * define if path should be changed for session construction (file can be moved)
   */
  default @Nullable FilePath getUsedFilePath(T session) {
    return null;
  }

  default @Nullable Cacheable getAdditionallyCachedData(T session) {
    return null;
  }
}
