// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VcsLogIndexUtils")
package com.intellij.vcs.log.data.index

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.ui.actions.ResumeIndexingAction.Companion.isBig
import com.intellij.vcs.log.ui.actions.ResumeIndexingAction.Companion.isScheduledForIndexing

/**
 * Check if any of [VcsLogModifiableIndex.getIndexingRoots] need to be indexed.
 */
fun needIndexing(index: VcsLogModifiableIndex): Boolean {
  val rootsForIndexing = index.indexingRoots
  if (rootsForIndexing.isEmpty()) return false
  val scheduledForIndexing = rootsForIndexing.filter { it.isScheduledForIndexing(index) }
  val bigRepositories = rootsForIndexing.filter { it.isBig() }

  return bigRepositories.isNotEmpty() || scheduledForIndexing.isNotEmpty()
}

/**
 * Check if VCS log indexing was paused in any of [VcsLogModifiableIndex.getIndexingRoots].
 */
fun isIndexingPaused(index: VcsLogModifiableIndex): Boolean {
  val rootsForIndexing = index.indexingRoots
  val scheduledForIndexing = rootsForIndexing.filter { it.isScheduledForIndexing(index) }

  return scheduledForIndexing.isEmpty()
}

/**
 * Resume Log indexing if paused or pause indexing if indexing is in progress.
 */
@RequiresEdt
internal fun toggleIndexing(rootsForIndexing: Set<VirtualFile>, index: VcsLogIndex) {
  if (rootsForIndexing.any { it.isScheduledForIndexing(index) }) {
    rootsForIndexing.filter { !it.isBig() }.forEach { VcsLogBigRepositoriesList.getInstance().addRepository(it) }
  }
  else {
    var resumed = false
    for (root in rootsForIndexing.filter { it.isBig() }) {
      resumed = resumed or VcsLogBigRepositoriesList.getInstance().removeRepository(root)
    }
    if (resumed) (index as? VcsLogModifiableIndex)?.scheduleIndex(false)
  }
}
