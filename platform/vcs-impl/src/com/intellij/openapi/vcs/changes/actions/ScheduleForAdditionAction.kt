// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.VcsNotificationIdsHolder.Companion.ADD_UNVERSIONED_ERROR
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.CommitDialogChangesBrowser
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.Consumer
import com.intellij.util.IconUtil
import com.intellij.util.PairConsumer
import com.intellij.util.containers.JBIterable
import org.jetbrains.annotations.Nls

open class ScheduleForAdditionAction : AnAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    val enabled = isEnabled(e)

    e.presentation.isEnabled = enabled
    if (ActionPlaces.ACTION_PLACE_VCS_QUICK_LIST_POPUP_ACTION == e.place ||
        ActionPlaces.CHANGES_VIEW_POPUP == e.place) {
      e.presentation.isVisible = enabled
    }
    if (e.isFromActionToolbar && e.presentation.icon == null) {
      e.presentation.icon = IconUtil.getAddIcon()
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val unversionedFiles = getUnversionedFiles(e, project).toList()

    performUnversionedFilesAddition(project, unversionedFiles, e.getData(ChangesBrowserBase.DATA_KEY), null)
  }

  protected open fun isEnabled(e: AnActionEvent): Boolean {
    val project = e.project
    return project != null && getUnversionedFiles(e, project).isNotEmpty
  }

  protected fun performUnversionedFilesAddition(project: Project,
                                                files: List<VirtualFile>,
                                                browser: ChangesBrowserBase?,
                                                additionalTask: PairConsumer<in ProgressIndicator, in MutableList<VcsException>>?) {
    if (files.isEmpty() && additionalTask == null) return

    val targetChangeList = when (browser) {
      is CommitDialogChangesBrowser -> browser.selectedChangeList
      else -> ChangeListManager.getInstance(project).defaultChangeList
    }

    val changesConsumer = if (browser is CommitDialogChangesBrowser) {
      Consumer { changes: List<Change> -> browser.viewer.includeChanges(changes) }
    }
    else null

    FileDocumentManager.getInstance().saveAllDocuments()

    if (ModalityState.current() == ModalityState.NON_MODAL) {
      addUnversionedFilesToVcsInBackground(project, targetChangeList, files, changesConsumer, additionalTask)
    }
    else {
      addUnversionedFilesToVcs(project, targetChangeList, files, changesConsumer, additionalTask)
    }
  }

  companion object {
    fun getUnversionedFiles(e: AnActionEvent, project: Project): JBIterable<VirtualFile> {
      return getUnversionedFiles(e.dataContext, project)
    }

    fun getUnversionedFiles(context: DataContext, project: Project): JBIterable<VirtualFile> {
      val filePaths = JBIterable.from(context.getData(ChangesListView.UNVERSIONED_FILE_PATHS_DATA_KEY))

      if (filePaths.isNotEmpty) {
        return filePaths.map { it.virtualFile }.filterNotNull()
      }

      // As an optimization, we assume that if {@link ChangesListView#UNVERSIONED_FILES_DATA_KEY} is empty, but {@link VcsDataKeys#CHANGES} is
      // not, then there will be either versioned (files from changes, hijacked files, locked files, switched files) or ignored files in
      // {@link VcsDataKeys#VIRTUAL_FILE_STREAM}. So there will be no files with {@link FileStatus#UNKNOWN} status, and we should not explicitly
      // check {@link VcsDataKeys#VIRTUAL_FILE_STREAM} files in this case.
      if (!ArrayUtil.isEmpty(context.getData(VcsDataKeys.CHANGES))) return JBIterable.empty()

      val vcsManager = ProjectLevelVcsManager.getInstance(project)
      val changeListManager = ChangeListManager.getInstance(project)
      return JBIterable.from(context.getData(VcsDataKeys.VIRTUAL_FILES))
        .filter { file: VirtualFile -> isFileUnversioned(file, vcsManager, changeListManager) }
    }

    private fun isFileUnversioned(file: VirtualFile,
                                  vcsManager: ProjectLevelVcsManager,
                                  changeListManager: ChangeListManager): Boolean {
      val vcs = vcsManager.getVcsFor(file)
      val fileStatus = changeListManager.getStatus(file)
      return fileStatus === FileStatus.UNKNOWN ||
             fileStatus !== FileStatus.IGNORED && vcs != null && !vcs.areDirectoriesVersionedItems() && file.isDirectory
    }

    @JvmStatic
    fun addUnversionedFilesToVcs(project: Project,
                                 targetChangeList: LocalChangeList?,
                                 files: List<VirtualFile>): Boolean {
      return addUnversionedFilesToVcs(project, targetChangeList, files, null, null)
    }

    @JvmStatic
    fun addUnversionedFilesToVcsInBackground(project: Project,
                                             targetChangeList: LocalChangeList?,
                                             files: List<VirtualFile>) {
      addUnversionedFilesToVcsInBackground(project, targetChangeList, files, null, null)
    }

    @JvmStatic
    fun addUnversionedFilesToVcsInBackground(project: Project,
                                             targetChangeList: LocalChangeList?,
                                             files: List<VirtualFile>,
                                             changesConsumer: Consumer<in List<Change>>?,
                                             additionalTask: PairConsumer<in ProgressIndicator, in MutableList<VcsException>>?) {
      runBackgroundableTask(VcsBundle.message("progress.title.adding.files.to.vcs"), project, true) { indicator ->
        val exceptions = mutableListOf<VcsException>()
        try {
          val allProcessedFiles = performUnversionedFilesAddition(project, files, exceptions)
          additionalTask?.consume(indicator, exceptions)
          moveAddedChangesTo(project, targetChangeList, allProcessedFiles, changesConsumer)
        }
        finally {
          if (exceptions.isNotEmpty()) {
            VcsNotifier.getInstance(project).notifyError(ADD_UNVERSIONED_ERROR,
                                                         VcsBundle.message("error.adding.files.notification.title"),
                                                         createErrorMessage(exceptions))
          }
        }
      }
    }

    @JvmStatic
    fun addUnversionedFilesToVcs(project: Project,
                                 targetChangeList: LocalChangeList?,
                                 files: List<VirtualFile>,
                                 changesConsumer: Consumer<in List<Change>>?,
                                 additionalTask: PairConsumer<in ProgressIndicator, in MutableList<VcsException>>?): Boolean {
      val exceptions = mutableListOf<VcsException>()

      runModalTask(VcsBundle.message("progress.title.adding.files.to.vcs"), project, true) { indicator ->
        val allProcessedFiles = performUnversionedFilesAddition(project, files, exceptions)
        additionalTask?.consume(indicator, exceptions)
        moveAddedChangesTo(project, targetChangeList, allProcessedFiles, changesConsumer)
      }

      if (exceptions.isNotEmpty()) {
        Messages.showErrorDialog(project, createErrorMessage(exceptions), VcsBundle.message("error.adding.files.title"))
      }

      return exceptions.isEmpty()
    }

    private fun performUnversionedFilesAddition(project: Project,
                                                files: List<VirtualFile>,
                                                exceptions: MutableList<VcsException>): Set<VirtualFile> {
      val allProcessedFiles = mutableSetOf<VirtualFile>()

      ChangesUtil.processVirtualFilesByVcs(project, files) { vcs: AbstractVcs, vcsFiles: List<VirtualFile> ->
        performUnversionedFilesAdditionForVcs(project, vcs, vcsFiles, allProcessedFiles, exceptions)
      }

      VcsDirtyScopeManager.getInstance(project).filesDirty(allProcessedFiles, null)

      return allProcessedFiles
    }

    private fun moveAddedChangesTo(project: Project,
                                   targetList: LocalChangeList?,
                                   allProcessedFiles: Set<VirtualFile>,
                                   changesConsumer: Consumer<in List<Change>>?) {
      val changeListManager = ChangeListManager.getInstance(project)
      val moveRequired = targetList != null && !targetList.isDefault &&
                         allProcessedFiles.isNotEmpty() &&
                         changeListManager.areChangeListsEnabled()
      val syncUpdateRequired = changesConsumer != null

      if (!moveRequired && !syncUpdateRequired) return

      ChangeListManagerEx.getInstanceEx(project).waitForUpdate()

      val newChanges = changeListManager.defaultChangeList.changes.filter { change: Change ->
        val file = ChangesUtil.getAfterPath(change)?.virtualFile
        file != null && allProcessedFiles.contains(file)
      }

      val changesMoved = moveRequired && newChanges.isNotEmpty()
      if (changesMoved) {
        changeListManager.moveChangesTo(targetList!!, newChanges)
      }

      if (changesConsumer != null) {
        ApplicationManager.getApplication().invokeAndWait {
          notifyChangesConsumer(project, changesConsumer, targetList, newChanges, rereadChanges = changesMoved)
        }
      }
    }

    private fun notifyChangesConsumer(project: Project,
                                      changesConsumer: Consumer<in List<Change>>,
                                      targetList: LocalChangeList?,
                                      newChanges: List<Change>,
                                      rereadChanges: Boolean) {
      val changeListManager = ChangeListManager.getInstance(project)

      val changes: List<Change>
      if (rereadChanges) {
        val newList = changeListManager.getChangeList(targetList!!.id)
        if (newList != null) {
          // 'newChanges' may contain ChangeListChange instances from the active changelist.
          // We need to obtain changes again from the up-to-date changelist to pass to callback.
          changes = newList.changes.intersect(newChanges.toSet()).toList()
        }
        else {
          logger<ScheduleForAdditionAction>().warn("Changelist not found after moving new changes: $targetList")
          changes = newChanges
        }
      }
      else {
        changes = newChanges
      }

      changesConsumer.consume(changes)
    }

    private fun performUnversionedFilesAdditionForVcs(project: Project,
                                                      vcs: AbstractVcs,
                                                      items: List<VirtualFile>,
                                                      allProcessedFiles: MutableSet<in VirtualFile>,
                                                      exceptions: MutableList<in VcsException>) {
      val environment = vcs.checkinEnvironment ?: return

      val descendants = getUnversionedDescendantsRecursively(project, vcs, items)
      val parents = getUnversionedParents(project, vcs, items)

      // it is assumed that not-added parents of files passed to scheduleUnversionedFilesForAddition() will also be added to vcs
      // (inside the method) - so common add logic just needs to refresh statuses of parents
      val exs = environment.scheduleUnversionedFilesForAddition(descendants.toList())
      if (exs != null) exceptions.addAll(exs)

      allProcessedFiles.addAll(descendants)
      allProcessedFiles.addAll(parents)
    }

    private fun getUnversionedDescendantsRecursively(project: Project, vcs: AbstractVcs, items: List<VirtualFile>): Set<VirtualFile> {
      val roots = items.toSet()

      val vcsManager = ProjectLevelVcsManager.getInstance(project)
      val unversionedPaths = ChangeListManagerImpl.getInstanceImpl(project).unversionedFilesPaths

      val result = mutableSetOf<VirtualFile>()
      for (path in unversionedPaths) {
        if (vcsManager.getVcsFor(path) != vcs) continue
        val file = path.virtualFile ?: continue
        if (hasAncestorIn(file, roots)) {
          result += file
        }
      }

      return result
    }

    private fun hasAncestorIn(file: VirtualFile, roots: Set<VirtualFile>): Boolean {
      return generateSequence(file) { it.parent }.any { roots.contains(it) }
    }

    private fun getUnversionedParents(project: Project,
                                      vcs: AbstractVcs,
                                      items: Collection<VirtualFile>): Set<VirtualFile> {
      if (!vcs.areDirectoriesVersionedItems()) return emptySet()

      val changeListManager = ChangeListManager.getInstance(project)
      val result = mutableSetOf<VirtualFile>()

      for (item in items) {
        var parent = item.parent

        while (parent != null && changeListManager.getStatus(parent) === FileStatus.UNKNOWN) {
          result.add(parent)
          parent = parent.parent
        }
      }

      return result
    }

    private fun createErrorMessage(exceptions: List<VcsException>): @Nls String {
      val message: @Nls StringBuilder = StringBuilder(VcsBundle.message("error.adding.files.prompt"))
      for (ex in exceptions) {
        message.append("\n").append(ex.message)
      }
      return message.toString() // NON-NLS
    }
  }
}