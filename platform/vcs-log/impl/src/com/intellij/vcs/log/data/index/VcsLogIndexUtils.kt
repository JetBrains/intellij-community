// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VcsLogIndexUtils")

package com.intellij.vcs.log.data.index

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Check if any of [VcsLogIndex.getIndexingRoots] need to be indexed.
 */
fun VcsLogIndex.needIndexing(): Boolean {
  val rootsForIndexing = indexingRoots
  if (rootsForIndexing.isEmpty()) return false

  val rootsScheduledForIndexing = rootsForIndexing.filter { isScheduledForIndexing(it) }
  val rootsWithPausedIndexing = rootsForIndexing.filter { isIndexingPausedFor(it) }

  return rootsWithPausedIndexing.isNotEmpty() || rootsScheduledForIndexing.isNotEmpty()
}

/**
 * Check if VCS log indexing was paused in all of [VcsLogIndex.getIndexingRoots].
 */
fun VcsLogIndex.isIndexingPaused(): Boolean {
  return indexingRoots.all { isIndexingPausedFor(it) }
}

/**
 * Check if VCS Log indexing was scheduled in any of the [VcsLogIndex.getIndexingRoots].
 */
fun VcsLogIndex.isIndexingScheduled(): Boolean {
  return indexingRoots.any { isScheduledForIndexing(it) }
}

/**
 * Pause VCS Log indexing if it is scheduled for any of the [VcsLogIndex.getIndexingRoots], try to resume it otherwise.
 */
@RequiresEdt
internal fun VcsLogModifiableIndex.toggleIndexing() {
  if (isIndexingScheduled()) {
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