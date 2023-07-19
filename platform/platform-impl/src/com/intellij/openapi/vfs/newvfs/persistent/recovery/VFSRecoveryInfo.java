// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.recovery;

import com.intellij.openapi.vfs.newvfs.persistent.VFSLoadException;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public class VFSRecoveryInfo {
  /** Errors detected during loading, but successfully 'fixed' by {@link Recoverer}s */
  public final List<VFSLoadException> recoveredErrors;

  /**
   * true if during the recovery content storage was re-created, and previous contentIds are now
   * invalid (i.e. LocalHistory needs to be cleared then)
   */
  public final boolean invalidateContentIds;

  private final IntList directoriesIdsToRefresh;

  public VFSRecoveryInfo(@NotNull List<VFSLoadException> recoveredErrors,
                         boolean invalidateContentIds,
                         @NotNull IntList directoriesIdsToRefresh) {
    this.recoveredErrors = Collections.unmodifiableList(recoveredErrors);
    this.invalidateContentIds = invalidateContentIds;
    this.directoriesIdsToRefresh = new IntArrayList(directoriesIdsToRefresh);
  }

  public IntList directoriesIdsToRefresh() {
    return new IntArrayList(directoriesIdsToRefresh);
  }

  @Override
  public String toString() {
    return "RecoveryInfo[" +
           "recoveredErrors: " + recoveredErrors.size() +
           ", invalidateContentIds: " + invalidateContentIds +
           ", directoriesIdsToRefresh: " + directoriesIdsToRefresh.size() +
           '}';
  }
}
