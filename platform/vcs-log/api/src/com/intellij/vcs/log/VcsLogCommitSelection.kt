// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log

import java.util.function.Consumer

/**
 * Selection of commits in the Vcs Log Graph.
 *
 * @see VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION
 */
interface VcsLogCommitSelection {

  /**
   * Rows in [com.intellij.vcs.log.graph.VisibleGraph]
   */
  val rows: IntArray

  /**
   * Identifiers of the commits selected in the table.
   *
   * @see VcsLogDataProvider.getCommitIndex
   */
  val ids: List<Int>

  /**
   * [CommitId] of the commits selected in the table.
   */
  val commits: List<CommitId>

  /**
   * Cached metadata of the commits selected in the table.
   * When metadata of the commit is not available in the cache, a placeholder object
   * (an instance of [com.intellij.vcs.log.data.LoadingDetails]) is returned.
   *
   * Metadata are loaded faster than full details and since it is done while scrolling,
   * there is a better chance that details for a commit are loaded when user selects it.
   *
   * @see com.intellij.vcs.log.data.LoadingDetails
   */
  val cachedMetadata: List<VcsCommitMetadata>

  /**
   * Cached full details of the commits selected in the table.
   * When full details of the commit are not available in the cache, a placeholder object
   * (an instance of [com.intellij.vcs.log.data.LoadingDetails]) is returned.
   *
   * @see com.intellij.vcs.log.data.LoadingDetails
   */
  val cachedFullDetails: List<VcsFullCommitDetails>

  /**
   * Sends a request to load full details of the selected commits in a background thread.
   * After all details are loaded they are provided to the consumer in the EDT.
   *
   * @param consumer called in EDT after all details are loaded.
   */
  fun requestFullDetails(consumer: Consumer<in List<VcsFullCommitDetails>>)
}