// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VcsLogIndexUtils")

package com.intellij.vcs.log.data.index

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Check if any of [VcsLogModifiableIndex.getIndexingRoots] need to be indexed.
 */
fun VcsLogModifiableIndex.needIndexing(): Boolean {
  val rootsForIndexing = indexingRoots
  if (rootsForIndexing.isEmpty()) return false

  val rootsScheduledForIndexing = rootsForIndexing.filter { isScheduledForIndexing(it) }
  val rootsWithPausedIndexing = rootsForIndexing.filter { isIndexingPausedFor(it) }

  return rootsWithPausedIndexing.isNotEmpty() || rootsScheduledForIndexing.isNotEmpty()
}

/**
 * Check if VCS log indexing was paused in any of [VcsLogModifiableIndex.getIndexingRoots].
 */
fun VcsLogModifiableIndex.isIndexingPaused(): Boolean {
  val scheduledForIndexing = indexingRoots.filter { isScheduledForIndexing(it) }

  return scheduledForIndexing.isEmpty()
}

/**
 * Resume Log indexing if paused or pause indexing if indexing is in progress.
 */
@RequiresEdt
internal fun VcsLogModifiableIndex.toggleIndexing() {
  if (indexingRoots.any { isScheduledForIndexing(it) }) {
    indexingRoots.filter { !isIndexingPausedFor(it) }.forEach { VcsLogBigRepositoriesList.getInstance().addRepository(it) }
  }
  else {
    var resumed = false
    for (root in indexingRoots.filter { isIndexingPausedFor(it) }) {
      resumed = resumed or VcsLogBigRepositoriesList.getInstance().removeRepository(root)
    }
    if (resumed) scheduleIndex(false)
  }
}

internal fun isIndexingPausedFor(root: VirtualFile): Boolean = VcsLogBigRepositoriesList.getInstance().isBig(root)
internal fun VcsLogIndex.isScheduledForIndexing(root: VirtualFile): Boolean = isIndexingEnabled(root) && !isIndexed(root)