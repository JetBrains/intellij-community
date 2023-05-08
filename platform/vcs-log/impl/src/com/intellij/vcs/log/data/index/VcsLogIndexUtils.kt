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
fun VcsLogModifiableIndex.needIndexing(): Boolean {
  val rootsForIndexing = indexingRoots
  if (rootsForIndexing.isEmpty()) return false
  val scheduledForIndexing = rootsForIndexing.filter { it.isScheduledForIndexing(this) }
  val bigRepositories = rootsForIndexing.filter { it.isBig() }

  return bigRepositories.isNotEmpty() || scheduledForIndexing.isNotEmpty()
}

/**
 * Check if VCS log indexing was paused in any of [VcsLogModifiableIndex.getIndexingRoots].
 */
fun VcsLogModifiableIndex.isIndexingPaused(): Boolean {
  val scheduledForIndexing = indexingRoots.filter { it.isScheduledForIndexing(this) }

  return scheduledForIndexing.isEmpty()
}

/**
 * Resume Log indexing if paused or pause indexing if indexing is in progress.
 */
@RequiresEdt
internal fun VcsLogIndex.toggleIndexing(rootsForIndexing: Set<VirtualFile>) {
  if (rootsForIndexing.any { it.isScheduledForIndexing(this) }) {
    rootsForIndexing.filter { !it.isBig() }.forEach { VcsLogBigRepositoriesList.getInstance().addRepository(it) }
  }
  else {
    var resumed = false
    for (root in rootsForIndexing.filter { it.isBig() }) {
      resumed = resumed or VcsLogBigRepositoriesList.getInstance().removeRepository(root)
    }
    if (resumed) (this as? VcsLogModifiableIndex)?.scheduleIndex(false)
  }
}
