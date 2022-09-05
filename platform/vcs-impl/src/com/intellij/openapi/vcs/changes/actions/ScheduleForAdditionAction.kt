// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.CommitDialogChangesBrowser
import com.intellij.openapi.vcs.impl.VcsRootIterator
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

    addUnversioned(project, unversionedFiles, e.getData(ChangesBrowserBase.DATA_KEY))
  }

  protected open fun isEnabled(e: AnActionEvent): Boolean {
    val project = e.project
    return project != null && getUnversionedFiles(e, project).isNotEmpty
  }

  companion object {
    @JvmStatic
    fun addUnversioned(project: Project,
                       files: List<VirtualFile>,
                       browser: ChangesBrowserBase?): Boolean {
      return addUnversioned(project, files, browser, null)
    }

    @JvmStatic
    protected fun addUnversioned(project: Project,
                                 files: List<VirtualFile>,
                                 browser: ChangesBrowserBase?,
                                 additionalTask: PairConsumer<in ProgressIndicator, in MutableList<VcsException>>?): Boolean {
      if (files.isEmpty() && additionalTask == null) return true

      val targetChangeList = when (browser) {
        is CommitDialogChangesBrowser -> browser.selectedChangeList
        else -> ChangeListManager.getInstance(project).defaultChangeList
      }

      val changeConsumer = browser?.let { Consumer { changes: List<Change> -> browser.viewer.includeChanges(changes) } }

      FileDocumentManager.getInstance().saveAllDocuments()
      return addUnversionedFilesToVcs(project, targetChangeList, files, changeConsumer, additionalTask)
    }

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
                                 list: LocalChangeList?,
                                 files: List<VirtualFile>): Boolean {
      return addUnversionedFilesToVcs(project, list, files, null, null)
    }

    @JvmStatic
    fun addUnversionedFilesToVcs(project: Project,
                                 list: LocalChangeList?,
                                 files: List<VirtualFile>,
                                 changesConsumer: Consumer<in List<Change>>?,
                                 additionalTask: PairConsumer<in ProgressIndicator, in MutableList<VcsException>>?): Boolean {
      val changeListManager = ChangeListManager.getInstance(project)

      val exceptions: MutableList<VcsException> = ArrayList()
      val allProcessedFiles: MutableSet<VirtualFile?> = HashSet()

      ProgressManager.getInstance().run(object : Task.Modal(project, VcsBundle.message("progress.title.adding.files.to.vcs"), true) {
        override fun run(indicator: ProgressIndicator) {
          ChangesUtil.processVirtualFilesByVcs(project, files) { vcs: AbstractVcs, files: List<VirtualFile> ->
            addUnversionedFilesToVcs(project, vcs, files, allProcessedFiles, exceptions)
          }

          additionalTask?.consume(indicator, exceptions)
        }
      })

      if (!exceptions.isEmpty()) {
        val message: @Nls StringBuilder = StringBuilder(VcsBundle.message("error.adding.files.prompt"))
        for (ex in exceptions) {
          message.append("\n").append(ex.message)
        }
        Messages.showErrorDialog(project, message.toString(), VcsBundle.message("error.adding.files.title"))
      }

      VcsDirtyScopeManager.getInstance(project).filesDirty(allProcessedFiles, null)

      val moveRequired = list != null && !list.isDefault && !allProcessedFiles.isEmpty() && changeListManager.areChangeListsEnabled()
      val syncUpdateRequired = changesConsumer != null

      if (moveRequired || syncUpdateRequired) {
        // find the changes for the added files and move them to the necessary changelist
        val updateMode = if (syncUpdateRequired) InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE else InvokeAfterUpdateMode.BACKGROUND_NOT_CANCELLABLE

        changeListManager.invokeAfterUpdate(
          {
            var newChanges = changeListManager.defaultChangeList.changes.filter { change: Change ->
              val path = ChangesUtil.getAfterPath(change)
              path != null && allProcessedFiles.contains(path.virtualFile)
            }

            if (moveRequired && !newChanges.isEmpty()) {
              changeListManager.moveChangesTo(list!!, newChanges)
            }

            if (changesConsumer != null) {
              if (moveRequired && !newChanges.isEmpty()) {
                // newChanges contains ChangeListChange instances from active change list in case of partial changes
                // so we obtain necessary changes again from required change list to pass to callback
                val newList = changeListManager.getChangeList(list!!.id)
                if (newList != null) newChanges = ArrayList(newList.changes.intersect(newChanges.toSet()))
              }

              changesConsumer.consume(newChanges)
            }
          }, updateMode, VcsBundle.message("change.lists.manager.add.unversioned"), null)
      }

      return exceptions.isEmpty()
    }

    private fun addUnversionedFilesToVcs(project: Project,
                                         vcs: AbstractVcs,
                                         items: List<VirtualFile>,
                                         allProcessedFiles: MutableSet<in VirtualFile>,
                                         exceptions: MutableList<in VcsException>) {
      val environment = vcs.checkinEnvironment ?: return

      val descendants = runReadAction { getUnversionedDescendantsRecursively(project, items) }
      val parents = runReadAction { getUnversionedParents(project, vcs, items) }

      // it is assumed that not-added parents of files passed to scheduleUnversionedFilesForAddition() will also be added to vcs
      // (inside the method) - so common add logic just needs to refresh statuses of parents
      val exs = environment.scheduleUnversionedFilesForAddition(descendants.toList())
      if (exs != null) exceptions.addAll(exs)

      allProcessedFiles.addAll(descendants)
      allProcessedFiles.addAll(parents)
    }

    private fun getUnversionedDescendantsRecursively(project: Project,
                                                     items: List<VirtualFile>): Set<VirtualFile> {
      val changeListManager = ChangeListManager.getInstance(project)
      val result: MutableSet<VirtualFile> = HashSet()

      for (item in items) {
        VcsRootIterator.iterateVfUnderVcsRoot(project, item) { child: VirtualFile ->
          if (changeListManager.getStatus(child) === FileStatus.UNKNOWN) {
            result.add(child)
          }
          true
        }
      }

      return result
    }

    private fun getUnversionedParents(project: Project,
                                      vcs: AbstractVcs,
                                      items: Collection<VirtualFile>): Set<VirtualFile> {
      if (!vcs.areDirectoriesVersionedItems()) return emptySet()

      val changeListManager = ChangeListManager.getInstance(project)
      val result: MutableSet<VirtualFile> = HashSet()

      for (item in items) {
        var parent = item.parent

        while (parent != null && changeListManager.getStatus(parent) === FileStatus.UNKNOWN) {
          result.add(parent)
          parent = parent.parent
        }
      }

      return result
    }
  }
}