// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.YES
import com.intellij.openapi.ui.Messages.getQuestionIcon
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.SelectFilesDialog
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Functions.identity
import com.intellij.util.PairConsumer
import com.intellij.vcsUtil.VcsFileUtil
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
class ScheduleForAdditionWithIgnoredFilesConfirmationAction : ScheduleForAdditionAction() {
  override fun isEnabled(e: AnActionEvent): Boolean {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return false
    if (Manager.getUnversionedFiles(e, project).isNotEmpty) return true

    val changes = e.getData(VcsDataKeys.CHANGES)?.asSequence().orEmpty()
    val files = e.getData(VcsDataKeys.VIRTUAL_FILES)?.asSequence().orEmpty()

    return collectPathsFromChanges(project, changes).firstOrNull() != null ||
           collectPathsFromFiles(project, files).firstOrNull() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val browser = e.getData(ChangesBrowserBase.DATA_KEY)

    val changes = e.getData(VcsDataKeys.CHANGES)?.asSequence().orEmpty()
    val files = e.getData(VcsDataKeys.VIRTUAL_FILES)?.asSequence().orEmpty()

    val toAdd = HashSet<FilePath>()
    toAdd += collectPathsFromChanges(project, changes)
    toAdd += collectPathsFromFiles(project, files)

    val unversionedFiles = Manager.getUnversionedFiles(e, project).toList()

    val changeListManager = ChangeListManager.getInstance(project)
    val (ignored, toAddWithoutIgnored) = toAdd.partition(changeListManager::isIgnoredFile)
    val confirmedIgnored =
      confirmAddFilePaths(project, ignored,
                          this::dialogTitle,
                          this::dialogMessage,
                          message("confirmation.title.add.ignored.files.or.dirs"))

    val addToVcsTask =
      if (toAdd.isNotEmpty()) PairConsumer<ProgressIndicator, MutableList<VcsException>> { _, exceptions ->
        addPathsToVcs(project, toAddWithoutIgnored + confirmedIgnored, exceptions, confirmedIgnored.isNotEmpty())
      }
      else null

    Manager.performUnversionedFilesAddition(project, unversionedFiles, browser, addToVcsTask)
  }

  private fun dialogMessage(path: FilePath): @NlsContexts.DialogMessage String {
    val question =
      if (path.isDirectory) message("confirmation.message.add.ignored.single.directory")
      else message("confirmation.message.add.ignored.single.file")
    return question + "\n" + FileUtil.getLocationRelativeToUserHome(path.presentableUrl)
  }

  private fun dialogTitle(path: FilePath): @NlsContexts.DialogTitle String =
    if (path.isDirectory) message("confirmation.title.add.ignored.single.directory")
    else message("confirmation.title.add.ignored.single.file")

  private fun addPathsToVcs(project: Project,
                            toAdd: Collection<FilePath>,
                            exceptions: MutableList<VcsException>,
                            containsIgnored: Boolean) {
    VcsUtil.groupByRoots(project, toAdd, identity()).forEach { (vcsRoot, paths) ->
      try {
        val actionExtension = getExtensionFor(project, vcsRoot.vcs) ?: return

        actionExtension.doAddFiles(project, vcsRoot.path, paths, containsIgnored)
        VcsFileUtil.markFilesDirty(project, paths)
      }
      catch (ex: VcsException) {
        exceptions.add(ex)
      }
    }
  }

  private fun collectPathsFromChanges(project: Project, allChanges: Sequence<Change>): Sequence<FilePath> {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)

    return allChanges
      .filter { change ->
        val actionExtension = getExtensionFor(project, vcsManager.getVcsFor(ChangesUtil.getFilePath(change)))
        actionExtension != null && actionExtension.isStatusForAddition(change.fileStatus)
      }
      .map(ChangesUtil::getFilePath)
  }

  private fun collectPathsFromFiles(project: Project, allFiles: Sequence<VirtualFile>): Sequence<FilePath> {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    val changeListManager = ChangeListManager.getInstance(project)

    return allFiles
      .filter { file ->
        val actionExtension = getExtensionFor(project, vcsManager.getVcsFor(file))
        actionExtension != null &&
        changeListManager.getStatus(file).let { status ->
          if (file.isDirectory) actionExtension.isStatusForDirectoryAddition(status) else actionExtension.isStatusForAddition(status)
        }
      }
      .map(VcsUtil::getFilePath)
  }

  private fun getExtensionFor(project: Project, vcs: AbstractVcs?) =
    if (vcs == null) null
    else ScheduleForAdditionActionExtension.EP_NAME.findFirstSafe { it.getSupportedVcs(project) == vcs }
}

private fun confirmAddFilePaths(project: Project, paths: List<FilePath>,
                                singlePathDialogTitle: (FilePath) -> @NlsContexts.DialogTitle String,
                                singlePathDialogMessage: (FilePath) -> @NlsContexts.DialogMessage String,
                                @NlsContexts.DialogTitle multiplePathsDialogTitle: String): List<FilePath> {
  if (paths.isEmpty()) return paths

  if (paths.size == 1) {
    val path = paths.single()
    if (YES == Messages.showDialog(project,
                                   singlePathDialogMessage(path),
                                   singlePathDialogTitle(path), null,
                                   arrayOf(CommonBundle.getAddButtonText(), CommonBundle.getCancelButtonText()), 0, 1,
                                   getQuestionIcon())) {
      return paths
    }
  }
  else {
    val files = paths.mapNotNull(FilePath::getVirtualFile)
    val dlg = SelectFilesDialog.init(project, files, null, null, true, true,
                                     CommonBundle.getAddButtonText(), CommonBundle.getCancelButtonText())
    dlg.title = multiplePathsDialogTitle
    if (dlg.showAndGet()) {
      return dlg.selectedFiles.map(VcsUtil::getFilePath)
    }
  }

  return emptyList()
}