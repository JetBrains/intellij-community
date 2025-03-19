// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Prepares the initial {@link DataPack} and handles subsequent VCS Log refreshes.
 */
@ApiStatus.Internal
public interface VcsLogRefresher {

  /**
   * Returns the {@link DataPack} currently stored in this refresher.
   *
   * @return current {@link DataPack}
   */
  @NotNull DataPack getCurrentDataPack();

  /**
   * Asynchronously loads some recent commits from the VCS, builds the DataPack and queues to refresh everything. <br/>
   * This is called on log initialization.
   */
  void initialize();

  /**
   * Refreshes the log and builds the actual data pack.
   * Triggered by some event from the VCS which indicates that the log could change (e.g. new commits arrived).
   *
   * @param optimized if true, before refreshing actual data pack, "small" data pack will be built in addition,
   *                  such pack can be used to faster update some parts of the log (e.g., currently visible ones).
   */
  void refresh(@NotNull Collection<VirtualFile> rootsToRefresh, boolean optimized);
}