// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VcsLogIndexUtils")

package com.intellij.vcs.log.data.index

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.data.VcsLogData

internal fun isIndexingPausedFor(root: VirtualFile): Boolean = VcsLogBigRepositoriesList.getInstance().isBig(root)
internal fun VcsLogIndex.isScheduledForIndexing(root: VirtualFile): Boolean = isIndexingEnabled(root) && !isIndexed(root)

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
    resumeIndexing(indexingRoots.filter { isIndexingPausedFor(it) })
  }
}

private fun VcsLogModifiableIndex.resumeIndexing(roots: Collection<VirtualFile>) {
  var resumed = false
  for (root in roots) {
    resumed = resumed or VcsLogBigRepositoriesList.getInstance().removeRepository(root)
  }
  if (resumed) scheduleIndex(true)
}

/**
 * Enables indexing in the registry if it is disabled. Toggles indexing otherwise.
 *
 * @see [VcsLogModifiableIndex.toggleIndexing]
 */
internal fun VcsLogData.toggleIndexing() {
  enableIndexing(VcsLogPersistentIndex.getAvailableIndexers(logProviders).keys)

  val index = index as? VcsLogModifiableIndex ?: return
  if (index.indexingRoots.isEmpty()) return

  index.toggleIndexing()
}

/**
 * Enables indexing in the registry if it is disabled. Resumes indexing for the provided repositories if it was paused.
 */
internal fun VcsLogData.enableAndResumeIndexing(roots: Collection<VirtualFile>) {
  enableIndexing(roots)

  val index = index as? VcsLogModifiableIndex ?: return
  index.resumeIndexing(roots)
}

private fun enableIndexing(roots: Collection<VirtualFile>) {
  if (!VcsLogData.isIndexSwitchedOnInRegistry()) {
    roots.forEach { VcsLogBigRepositoriesList.getInstance().removeRepository(it) }
    VcsLogData.getIndexingRegistryValue().setValue(true)
  }
}