// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VcsLogIndexUtils")

package com.intellij.vcs.log.data.index

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsLogSharedSettings

fun isIndexingPausedFor(root: VirtualFile): Boolean = VcsLogBigRepositoriesList.getInstance().isBig(root)
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
  if (enableIndexing(project, VcsLogPersistentIndex.getAvailableIndexers(logProviders).keys)) return

  val index = index as? VcsLogModifiableIndex ?: return
  if (index.indexingRoots.isEmpty()) return

  index.toggleIndexing()
}

/**
 * Enables indexing in the registry and project settings if it was disabled. Resumes indexing for the provided repositories if it was paused.
 */
fun enableAndResumeIndexing(project: Project, data: VcsLogData?, roots: Collection<VirtualFile>) {
  if (enableIndexing(project, roots)) return

  val index = data?.index as? VcsLogModifiableIndex ?: return
  index.resumeIndexing(roots)
}

/**
 * Enables indexing in the registry and project settings if it was disabled. Returns true if indexing state changed.
 */
private fun enableIndexing(project: Project, roots: Collection<VirtualFile>): Boolean {
  if (project.isIndexingEnabled) return false

  roots.forEach { VcsLogBigRepositoriesList.getInstance().removeRepository(it) }
  VcsLogData.getIndexingRegistryValue().setValue(true)
  project.service<VcsLogSharedSettings>().isIndexSwitchedOn = true
  return true
}

val Project.isIndexingEnabled: Boolean
  get() {
    return VcsLogData.isIndexSwitchedOnInRegistry() && VcsLogSharedSettings.isIndexSwitchedOn(this)
  }