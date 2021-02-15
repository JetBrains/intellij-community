// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.getToolWindowFor
import com.intellij.openapi.vcs.ex.LocalRange
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.asJBIterable
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.Nls

class MoveChangesToAnotherListAction : AbstractChangeListAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    val enabled = project != null && ProjectLevelVcsManager.getInstance(project).hasActiveVcss() &&
                  ChangeListManager.getInstance(project).areChangeListsEnabled() &&
                  (!e.getData(ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY).asJBIterable().isEmpty() ||
                   !e.getData(VcsDataKeys.CHANGES).isNullOrEmpty() ||
                   !e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY).isNullOrEmpty())

    updateEnabledAndVisible(e, enabled)

    if (!e.getData(VcsDataKeys.CHANGE_LISTS).isNullOrEmpty()) {
      e.presentation.text = ActionsBundle.message("action.ChangesView.Move.Files.text")
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    with(MoveChangesHandler(e.project!!)) {
      addChanges(e.getData(VcsDataKeys.CHANGES))
      if (isEmpty) {
        addChangesForFiles(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY))
      }

      if (isEmpty) {
        VcsBalloonProblemNotifier.showOverChangesView(project,
                                                      VcsBundle.message("move.to.another.changelist.nothing.selected.notification"),
                                                      MessageType.INFO)
        return
      }

      if (!askAndMove(project, changes, unversionedFiles)) return
      if (changedFiles.isNotEmpty()) {
        selectAndShowFile(project, changedFiles[0])
      }
    }
  }

  private fun selectAndShowFile(project: Project, file: VirtualFile) {
    val window = getToolWindowFor(project, LOCAL_CHANGES) ?: return
    if (!window.isVisible) {
      window.activate { ChangesViewManager.getInstance(project).selectFile(file) }
    }
  }

  companion object {
    @JvmStatic
    fun askAndMove(project: Project, changes: Collection<Change>, unversionedFiles: List<VirtualFile>): Boolean {
      if (changes.isEmpty() && unversionedFiles.isEmpty()) return false

      val targetList = askTargetList(project, changes)
      if (targetList != null) {
        val changeListManager = ChangeListManagerEx.getInstanceEx(project)

        changeListManager.moveChangesTo(targetList, *changes.toTypedArray())
        if (unversionedFiles.isNotEmpty()) {
          changeListManager.addUnversionedFiles(targetList, unversionedFiles)
        }
        return true
      }
      return false
    }

    private fun guessPreferredList(lists: Collection<LocalChangeList>): ChangeList? {
      val comparator = compareBy<LocalChangeList> { if (it.isDefault) -1 else 0 }
        .thenBy { if (it.changes.isEmpty()) -1 else 0 }
        .then(ChangesUtil.CHANGELIST_COMPARATOR)
      return lists.minWith(comparator)
    }

    private fun askTargetList(project: Project, changes: Collection<Change>): LocalChangeList? {
      val changeListManager = ChangeListManager.getInstance(project)

      val affectedLists = changes.flatMapTo(mutableSetOf()) { changeListManager.getChangeLists(it) }

      val sameFileChangeLists = changes.flatMapTo(mutableSetOf()) {
        val fileChange = if (it is ChangeListChange) it.change else it
        changeListManager.getChangeLists(fileChange)
      }

      return askTargetChangelist(project, affectedLists, sameFileChangeLists,
                                 ActionsBundle.message("action.ChangesView.Move.text"))
    }

    @JvmStatic
    fun askTargetChangelist(project: Project,
                            selectedRanges: List<LocalRange>,
                            tracker: PartialLocalLineStatusTracker): LocalChangeList? {
      val changeListManager = ChangeListManager.getInstance(project)
      val allChangelists = changeListManager.changeListsCopy

      val affectedListIds = selectedRanges.map { range -> range.changelistId }.toSet()
      val affectedLists = allChangelists.filter { list -> affectedListIds.contains(list.id) }.toSet()

      val sameFileChangeListsIds = tracker.getAffectedChangeListsIds().toSet()
      val sameFileChangeLists = allChangelists.filter { list -> sameFileChangeListsIds.contains(list.id) }.toSet()

      return askTargetChangelist(project, affectedLists, sameFileChangeLists,
                                 ActionsBundle.message("action.Vcs.MoveChangedLinesToChangelist.text"))
    }

    private fun askTargetChangelist(project: Project,
                                    affectedLists: Set<LocalChangeList>,
                                    sameFileChangeLists: Set<LocalChangeList>,
                                    title: @Nls String): LocalChangeList? {
      val changeListManager = ChangeListManager.getInstance(project)
      val allChangelists = changeListManager.changeListsCopy

      val nonAffectedLists = if (affectedLists.size == 1) allChangelists - affectedLists else allChangelists

      val suggestedLists = nonAffectedLists.ifEmpty { listOf(changeListManager.defaultChangeList) }

      val preferredList = (sameFileChangeLists - affectedLists)
        .ifEmpty { allChangelists.toSet() - affectedLists }
        .ifEmpty { nonAffectedLists }
      val defaultSelection = guessPreferredList(preferredList)

      val chooser = ChangeListChooser(project, suggestedLists, defaultSelection, title, null)
      chooser.show()
      return chooser.selectedList
    }
  }
}

private class MoveChangesHandler(val project: Project) {
  private val changeListManager = ChangeListManager.getInstance(project)

  val unversionedFiles = mutableListOf<VirtualFile>()
  val changedFiles = mutableListOf<VirtualFile>()
  val changes = mutableListOf<Change>()

  val isEmpty get() = changes.isEmpty() && unversionedFiles.isEmpty()

  fun addChanges(changes: Array<Change>?) {
    this.changes.addAll(changes.orEmpty())
  }

  fun addChangesForFiles(files: Array<VirtualFile>?) {
    for (file in files.orEmpty()) {
      val change = changeListManager.getChange(file)
      if (change == null) {
        val status = changeListManager.getStatus(file)
        if (FileStatus.UNKNOWN == status) {
          unversionedFiles.add(file)
          changedFiles.add(file)
        }
        else if (FileStatus.NOT_CHANGED == status && file.isDirectory) {
          addChangesUnder(VcsUtil.getFilePath(file))
        }
      }
      else {
        val afterPath = ChangesUtil.getAfterPath(change)
        if (afterPath != null && afterPath.isDirectory) {
          addChangesUnder(afterPath)
        }
        else {
          changes.add(change)
          changedFiles.add(file)
        }
      }
    }
  }

  private fun addChangesUnder(path: FilePath) {
    for (change in changeListManager.getChangesIn(path)) {
      changes.add(change)

      val file = ChangesUtil.getAfterPath(change)?.virtualFile
      file?.let { changedFiles.add(it) }
    }
  }
}