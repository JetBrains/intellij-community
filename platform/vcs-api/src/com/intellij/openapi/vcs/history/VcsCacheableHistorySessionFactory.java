// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.List;

/**
 * @author irengrig
 */
public interface VcsCacheableHistorySessionFactory<Cacheable extends Serializable, T extends VcsAbstractHistorySession> {

  T createFromCachedData(@Nullable Cacheable cacheable,
                         @NotNull List<? extends VcsFileRevision> revisions,
                         @NotNull FilePath filePath,
                         @Nullable VcsRevisionNumber currentRevision);

  /**
   * define if path should be changed for session construction (file can be moved)
   */
  @Nullable
  default FilePath getUsedFilePath(T session) {
    return null;
  }

  @Nullable
  default Cacheable getAdditionallyCachedData(T session) {
    return getAddinionallyCachedData(session);
  }

  /**
   * @deprecated implement {@link #getAdditionallyCachedData(VcsAbstractHistorySession)}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(forRemoval = true)
  @Nullable
  default Cacheable getAddinionallyCachedData(T session) {
    return null;
  }
}
