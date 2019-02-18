// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
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
import com.intellij.util.ArrayUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.isEmpty
import com.intellij.vcsUtil.VcsUtil
import java.util.*
import java.util.stream.Stream

/**
 * @author max
 */
class MoveChangesToAnotherListAction : AnAction(ActionsBundle.actionText(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST),
                                                ActionsBundle.actionDescription(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST), null), DumbAware {

  override fun update(e: AnActionEvent) {
    val isEnabled = isEnabled(e)

    if (ActionPlaces.isPopupPlace(e.place)) {
      e.presentation.isEnabledAndVisible = isEnabled
    }
    else {
      e.presentation.isEnabled = isEnabled
    }
  }

  protected fun isEnabled(e: AnActionEvent): Boolean {
    val project = e.getData(CommonDataKeys.PROJECT)
    return if (project == null || !ProjectLevelVcsManager.getInstance(project).hasActiveVcss()) {
      false
    }
    else !e.getData<Stream<VirtualFile>>(ChangesListView.UNVERSIONED_FILES_DATA_KEY).isEmpty() ||
         !ArrayUtil.isEmpty(e.getData(VcsDataKeys.CHANGES)) ||
         !ArrayUtil.isEmpty(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY))

  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val changesList = ContainerUtil.newArrayList<Change>()

    val changes = e.getData(VcsDataKeys.CHANGES)
    if (changes != null) {
      ContainerUtil.addAll<Change, Change, List<Change>>(changesList, *changes)
    }

    val unversionedFiles = ContainerUtil.newArrayList<VirtualFile>()
    val changedFiles = ContainerUtil.newArrayList<VirtualFile>()
    val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
    if (files != null && changesList.isEmpty()) {
      changesList.addAll(getChangesForSelectedFiles(project, files, unversionedFiles, changedFiles))
    }

    if (changesList.isEmpty() && unversionedFiles.isEmpty()) {
      VcsBalloonProblemNotifier.showOverChangesView(project, "Nothing is selected that can be moved", MessageType.INFO)
      return
    }

    if (!askAndMove(project, changesList, unversionedFiles)) return
    if (!changedFiles.isEmpty()) {
      selectAndShowFile(project, changedFiles[0])
    }
  }

  companion object {

    private fun getChangesForSelectedFiles(project: Project,
                                           selectedFiles: Array<VirtualFile>,
                                           unversionedFiles: MutableList<in VirtualFile>,
                                           changedFiles: MutableList<in VirtualFile>): List<Change> {
      val changes = ArrayList<Change>()
      val changeListManager = ChangeListManager.getInstance(project)

      for (vFile in selectedFiles) {
        val change = changeListManager.getChange(vFile)
        if (change == null) {
          val status = changeListManager.getStatus(vFile)
          if (FileStatus.UNKNOWN == status) {
            unversionedFiles.add(vFile)
            changedFiles.add(vFile)
          }
          else if (FileStatus.NOT_CHANGED == status && vFile.isDirectory) {
            addAllChangesUnderPath(changeListManager, VcsUtil.getFilePath(vFile), changes, changedFiles)
          }
        }
        else {
          val afterPath = ChangesUtil.getAfterPath(change)
          if (afterPath != null && afterPath.isDirectory) {
            addAllChangesUnderPath(changeListManager, afterPath, changes, changedFiles)
          }
          else {
            changes.add(change)
            changedFiles.add(vFile)
          }
        }
      }
      return changes
    }

    private fun addAllChangesUnderPath(changeListManager: ChangeListManager,
                                       file: FilePath,
                                       changes: MutableList<in Change>,
                                       changedFiles: MutableList<in VirtualFile>) {
      for (change in changeListManager.getChangesIn(file)) {
        changes.add(change)

        val path = ChangesUtil.getAfterPath(change)
        if (path != null && path.virtualFile != null) {
          changedFiles.add(path.virtualFile!!)
        }
      }
    }

    private fun selectAndShowFile(project: Project, file: VirtualFile) {
      val window = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)

      if (!window.isVisible) {
        window.activate { ChangesViewManager.getInstance(project).selectFile(file) }
      }
    }

    @JvmStatic
    fun askAndMove(project: Project,
                   changes: Collection<Change>,
                   unversionedFiles: List<VirtualFile>): Boolean {
      if (changes.isEmpty() && unversionedFiles.isEmpty()) return false

      val targetList = askTargetList(project, changes)

      if (targetList != null) {
        val listManager = ChangeListManagerImpl.getInstanceImpl(project)

        listManager.moveChangesTo(targetList, *changes.toTypedArray())
        if (!unversionedFiles.isEmpty()) {
          listManager.addUnversionedFiles(targetList, unversionedFiles)
        }
        return true
      }
      return false
    }

    private fun askTargetList(project: Project, changes: Collection<Change>): LocalChangeList? {
      val listManager = ChangeListManagerImpl.getInstanceImpl(project)
      val nonAffectedLists = getNonAffectedLists(listManager.changeListsCopy, changes)
      val suggestedLists = if (nonAffectedLists.isEmpty())
        listOf(listManager.defaultChangeList)
      else
        nonAffectedLists
      val defaultSelection = guessPreferredList(nonAffectedLists)

      val chooser = ChangeListChooser(project, suggestedLists, defaultSelection,
                                      ActionsBundle.message("action.ChangesView.Move.text"), null)
      chooser.show()

      return chooser.selectedList
    }

    @JvmStatic
    fun guessPreferredList(lists: List<LocalChangeList>): ChangeList? {
      val activeChangeList = ContainerUtil.find(lists) { it.isDefault }
      if (activeChangeList != null) return activeChangeList

      val emptyList = ContainerUtil.find(lists) { list -> list.changes.isEmpty() }

      return ObjectUtils.chooseNotNull(emptyList, ContainerUtil.getFirstItem(lists))
    }

    private fun getNonAffectedLists(lists: List<LocalChangeList>, changes: Collection<Change>): List<LocalChangeList> {
      val changesSet = ContainerUtil.newHashSet(changes)

      return ContainerUtil.findAll(lists) { list -> !ContainerUtil.intersects(changesSet, list.changes) }
    }
  }
}
