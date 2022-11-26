// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl

import com.intellij.diff.util.Side
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.conflicts.ChangelistConflictTracker
import com.intellij.openapi.vcs.ex.ExclusionState
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PairFunction
import com.intellij.util.containers.MultiMap
import com.intellij.util.ui.ThreeStateCheckBox

object PartialChangesUtil {
  private val LOG = Logger.getInstance(PartialChangesUtil::class.java)

  @JvmStatic
  fun getPartialTracker(project: Project, change: Change): PartialLocalLineStatusTracker? {
    val file = getVirtualFile(change) ?: return null
    return getPartialTracker(project, file)
  }

  @JvmStatic
  fun getPartialTracker(project: Project, file: VirtualFile): PartialLocalLineStatusTracker? {
    val tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(file)
    return tracker as? PartialLocalLineStatusTracker
  }

  @JvmStatic
  fun getVirtualFile(change: Change): VirtualFile? {
    val revision = change.afterRevision as? CurrentContentRevision
    return revision?.virtualFile
  }

  @JvmStatic
  fun processPartialChanges(project: Project,
                            changes: Collection<Change>,
                            executeOnEDT: Boolean,
                            partialProcessor: PairFunction<in List<ChangeListChange>, in PartialLocalLineStatusTracker, Boolean>): List<Change> {
    if (!LineStatusTrackerManager.getInstance(project).arePartialChangelistsEnabled() ||
        changes.none { it is ChangeListChange }) {
      return changes.toMutableList()
    }

    val otherChanges = mutableListOf<Change>()
    val task = Runnable {
      val partialChangesMap = MultiMap<VirtualFile, ChangeListChange>()
      for (change in changes) {
        if (change is ChangeListChange) {
          val virtualFile = getVirtualFile(change)
          if (virtualFile != null) {
            partialChangesMap.putValue(virtualFile, change)
          }
          else {
            otherChanges.add(change.change)
          }
        }
        else {
          otherChanges.add(change)
        }
      }

      val lstManager = LineStatusTrackerManager.getInstance(project)
      for ((virtualFile, value) in partialChangesMap.entrySet()) {
        @Suppress("UNCHECKED_CAST") val partialChanges = value as List<ChangeListChange>
        val actualChange = partialChanges[0].change
        val tracker = lstManager.getLineStatusTracker(virtualFile) as? PartialLocalLineStatusTracker
        if (tracker == null ||
            !partialProcessor.`fun`(partialChanges, tracker)) {
          otherChanges.add(actualChange)
        }
      }
    }

    if (executeOnEDT && !ApplicationManager.getApplication().isDispatchThread) {
      ApplicationManager.getApplication().invokeAndWait(task)
    }
    else {
      task.run()
    }
    return otherChanges
  }

  @JvmStatic
  fun runUnderChangeList(project: Project,
                         targetChangeList: LocalChangeList?,
                         task: Runnable) {
    computeUnderChangeList(project, targetChangeList) {
      task.run()
      null
    }
  }

  @JvmStatic
  fun <T> computeUnderChangeList(project: Project,
                                 targetChangeList: LocalChangeList?,
                                 task: Computable<T>): T {
    val changeListManager = ChangeListManagerEx.getInstanceEx(project)
    val oldDefaultList = changeListManager.defaultChangeList
    if (targetChangeList == null ||
        targetChangeList == oldDefaultList ||
        !changeListManager.areChangeListsEnabled()) {
      return task.compute()
    }

    switchChangeList(changeListManager, targetChangeList, oldDefaultList)
    val clmConflictTracker = ChangelistConflictTracker.getInstance(project)
    try {
      clmConflictTracker.setIgnoreModifications(true)
      return task.compute()
    }
    finally {
      clmConflictTracker.setIgnoreModifications(false)
      restoreChangeList(changeListManager, targetChangeList, oldDefaultList)
    }
  }

  suspend fun <T> underChangeList(project: Project,
                                  targetChangeList: LocalChangeList?,
                                  task: suspend () -> T): T {
    val changeListManager = ChangeListManagerEx.getInstanceEx(project)
    val oldDefaultList = changeListManager.defaultChangeList
    if (targetChangeList == null ||
        targetChangeList == oldDefaultList ||
        !changeListManager.areChangeListsEnabled()) {
      return task()
    }

    switchChangeList(changeListManager, targetChangeList, oldDefaultList)
    val clmConflictTracker = ChangelistConflictTracker.getInstance(project)
    try {
      clmConflictTracker.setIgnoreModifications(true)
      return task()
    }
    finally {
      clmConflictTracker.setIgnoreModifications(false)
      restoreChangeList(changeListManager, targetChangeList, oldDefaultList)
    }
  }

  @JvmStatic
  fun <T> computeUnderChangeListSync(project: Project,
                                     targetChangeList: LocalChangeList?,
                                     task: Computable<T>): T {
    val changeListManager = ChangeListManagerEx.getInstanceEx(project)
    val oldDefaultList = changeListManager.defaultChangeList
    if (targetChangeList == null ||
        !changeListManager.areChangeListsEnabled()) {
      return task.compute()
    }

    switchChangeList(changeListManager, targetChangeList, oldDefaultList)
    val clmConflictTracker = ChangelistConflictTracker.getInstance(project)
    try {
      clmConflictTracker.setIgnoreModifications(true)
      return task.compute()
    }
    finally {
      clmConflictTracker.setIgnoreModifications(false)
      if (ApplicationManager.getApplication().isReadAccessAllowed) {
        LOG.warn("Can't wait till changes are applied while holding read lock", Throwable())
      }
      else {
        ChangeListManagerEx.getInstanceEx(project).waitForUpdate()
      }
      restoreChangeList(changeListManager, targetChangeList, oldDefaultList)
    }
  }

  private fun switchChangeList(clm: ChangeListManagerEx,
                               targetChangeList: LocalChangeList,
                               oldDefaultList: LocalChangeList) {
    clm.setDefaultChangeList(targetChangeList, true)
    LOG.debug("Active changelist changed: ${oldDefaultList.name} -> ${targetChangeList.name}")
  }

  private fun restoreChangeList(clm: ChangeListManagerEx,
                                targetChangeList: LocalChangeList,
                                oldDefaultList: LocalChangeList) {
    val defaultChangeList = clm.defaultChangeList
    if (defaultChangeList.id == targetChangeList.id) {
      clm.setDefaultChangeList(oldDefaultList, true)
      LOG.debug("Active changelist restored: ${targetChangeList.name} -> ${oldDefaultList.name}")
    }
    else {
      LOG.warn(Throwable("Active changelist was changed during the operation. " +
                         "Expected: ${targetChangeList.name} -> ${oldDefaultList.name}, " +
                         "actual default: ${defaultChangeList.name}"))
    }
  }

  @JvmStatic
  fun convertExclusionState(exclusionState: ExclusionState): ThreeStateCheckBox.State {
    return when (exclusionState) {
      ExclusionState.ALL_INCLUDED -> ThreeStateCheckBox.State.SELECTED
      ExclusionState.ALL_EXCLUDED -> ThreeStateCheckBox.State.NOT_SELECTED
      else -> ThreeStateCheckBox.State.DONT_CARE
    }
  }

  @JvmStatic
  fun wrapPartialChanges(project: Project, changes: List<Change>): List<Change> {
    return changes.map { change -> wrapPartialChangeIfNeeded(project, change) ?: change }
  }

  private fun wrapPartialChangeIfNeeded(project: Project, change: Change): Change? {
    if (change !is ChangeListChange) return null

    val afterRevision = change.afterRevision
    if (afterRevision !is CurrentContentRevision) return null

    val tracker = getPartialTracker(project, change)
    if (tracker == null || !tracker.isOperational() || !tracker.hasPartialChangesToCommit()) return null

    val partialAfterRevision = PartialContentRevision(project, tracker.virtualFile, change.changeListId, afterRevision)
    return ChangeListChange.replaceChangeContents(change, change.beforeRevision, partialAfterRevision)
  }

  private class PartialContentRevision(val project: Project,
                                       val virtualFile: VirtualFile,
                                       val changeListId: String,
                                       val delegate: ContentRevision) : ContentRevision {
    override fun getFile(): FilePath = delegate.file
    override fun getRevisionNumber(): VcsRevisionNumber = delegate.revisionNumber
    override fun getContent(): String? {
      val tracker = getPartialTracker(project, virtualFile)
      if (tracker != null && tracker.isOperational() && tracker.hasPartialChangesToCommit()) {
        val partialContent = tracker.getChangesToBeCommitted(Side.LEFT, listOf(changeListId), true)
        if (partialContent != null) return partialContent
        LOG.warn("PartialContentRevision - missing partial content for $tracker")
      }
      return delegate.content
    }
  }
}