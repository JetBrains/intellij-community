// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.containers.ContainerUtil.intersects
import com.intellij.util.containers.isEmpty
import com.intellij.vcsUtil.VcsUtil

class MoveChangesToAnotherListAction : AbstractChangeListAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    val enabled = project != null && ProjectLevelVcsManager.getInstance(project).hasActiveVcss() &&
                  (!e.getData(ChangesListView.UNVERSIONED_FILES_DATA_KEY).isEmpty() ||
                   !e.getData(VcsDataKeys.CHANGES).isNullOrEmpty() ||
                   !e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY).isNullOrEmpty())

    updateEnabledAndVisible(e, enabled)
  }

  override fun actionPerformed(e: AnActionEvent) {
    with(MoveChangesHandler(e.project!!)) {
      addChanges(e.getData(VcsDataKeys.CHANGES))
      if (isEmpty) {
        addChangesForFiles(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY))
      }

      if (isEmpty) {
        VcsBalloonProblemNotifier.showOverChangesView(project, "Nothing is selected that can be moved", MessageType.INFO)
        return
      }

      if (!askAndMove(project, changes, unversionedFiles)) return
      if (!changedFiles.isEmpty()) {
        selectAndShowFile(project, changedFiles[0])
      }
    }
  }

  private fun selectAndShowFile(project: Project, file: VirtualFile) {
    val window = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)

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
        val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)

        changeListManager.moveChangesTo(targetList, *changes.toTypedArray())
        if (!unversionedFiles.isEmpty()) {
          changeListManager.addUnversionedFiles(targetList, unversionedFiles)
        }
        return true
      }
      return false
    }

    @JvmStatic
    fun guessPreferredList(lists: List<LocalChangeList>): ChangeList? =
      lists.find { it.isDefault } ?: lists.find { it.changes.isEmpty() } ?: lists.firstOrNull()

    private fun askTargetList(project: Project, changes: Collection<Change>): LocalChangeList? {
      val changeListManager = ChangeListManager.getInstance(project)
      val nonAffectedLists = getNonAffectedLists(changeListManager.changeLists, changes)
      val suggestedLists = nonAffectedLists.ifEmpty { listOf(changeListManager.defaultChangeList) }
      val defaultSelection = guessPreferredList(nonAffectedLists)

      val chooser = ChangeListChooser(project, suggestedLists, defaultSelection, ActionsBundle.message("action.ChangesView.Move.text"),
                                      null)
      chooser.show()
      return chooser.selectedList
    }

    private fun getNonAffectedLists(lists: List<LocalChangeList>, changes: Collection<Change>): List<LocalChangeList> {
      val changesSet = changes.toSet()
      return lists.filter { !intersects(changesSet, it.changes) }
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