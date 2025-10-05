// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.vfs.VirtualFile

/**
 * Prepares the initial [DataPack] and handles subsequent VCS Log refreshes.
 */
internal interface VcsLogRefresher {
  /**
   * Returns the [DataPack] currently stored in this refresher.
   *
   * @return current [DataPack]
   */
  val currentDataPack: DataPack

  /**
   * Asynchronously loads some recent commits from the VCS, builds the DataPack and queues to refresh everything. <br></br>
   * This is called on log initialization.
   */
  fun initialize()

  /**
   * Refreshes the log and builds the actual data pack.
   * Triggered by some event from the VCS which indicates that the log could change (e.g. new commits arrived).
   *
   * @param optimized if true, before refreshing actual data pack, "small" data pack will be built in addition,
   * such pack can be used to faster update some parts of the log (e.g., currently visible ones).
   */
  fun refresh(rootsToRefresh: Collection<VirtualFile>, optimized: Boolean)
}