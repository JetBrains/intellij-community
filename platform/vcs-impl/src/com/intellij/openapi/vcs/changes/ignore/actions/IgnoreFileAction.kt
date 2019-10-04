// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ignore.actions

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.YES
import com.intellij.openapi.ui.Messages.getQuestionIcon
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.IgnoredBeanFactory
import com.intellij.openapi.vcs.changes.IgnoredFileBean
import com.intellij.openapi.vcs.changes.ignore.psi.util.addNewElements
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import kotlin.streams.toList

class IgnoreFileAction(private val ignoreFile: VirtualFile) : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val vcs = VcsUtil.getVcsFor(project, ignoreFile) ?: return

    val ignored = getIgnoredFileBeans(e, vcs)
    if (ignored.isEmpty()) return

    writeIgnoreFileEntries(project, ignoreFile, ignored)
  }

}

class CreateNewIgnoreFileAction(private val ignoreFileName: String, private val ignoreFileRoot: VirtualFile) : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val ignoreFileRootVcs = VcsUtil.getVcsFor(project, ignoreFileRoot) ?: return

    val ignored = getIgnoredFileBeans(e, ignoreFileRootVcs)
    if (ignored.isEmpty() || !confirmCreateIgnoreFile(project)) return

    val ignoreFile = runUndoTransparentWriteAction { ignoreFileRoot.createChildData(ignoreFileRoot, ignoreFileName) }
    writeIgnoreFileEntries(project, ignoreFile, ignored)
  }

  private fun confirmCreateIgnoreFile(project: Project) =
    YES == Messages.showDialog(project,
                               message("vcs.add.to.ignore.file.create.ignore.file.confirmation.message",
                                       ignoreFileName, FileUtil.getLocationRelativeToUserHome(ignoreFileRoot.presentableUrl)),
                               message("vcs.add.to.ignore.file.create.ignore.file.confirmation.title", ignoreFileName),
                               null, arrayOf(IdeBundle.message("button.create"), CommonBundle.getCancelButtonText()),
                               0, 1, getQuestionIcon())
}

fun writeIgnoreFileEntries(project: Project,
                           ignoreFile: VirtualFile,
                           ignored: List<IgnoredFileBean>,
                           vcs: AbstractVcs? = null,
                           ignoreEntryRoot: VirtualFile? = null) {
  addNewElements(project, ignoreFile, ignored, vcs, ignoreEntryRoot)
  ChangeListManagerImpl.getInstanceImpl(project).scheduleUnversionedUpdate()
  OpenFileDescriptor(project, ignoreFile).navigate(true)
}

fun getIgnoredFileBeans(e: AnActionEvent, vcs: AbstractVcs): List<IgnoredFileBean> {
  val project = e.getRequiredData(CommonDataKeys.PROJECT)
  val selectedFiles = getSelectedFiles(e)

  return selectedFiles
    .filter { VcsUtil.getVcsFor(project, it) == vcs }
    .map { IgnoredBeanFactory.ignoreFile(it, project) }
}

fun getSelectedFiles(e: AnActionEvent): List<VirtualFile> {
  val exactlySelectedFiles = e.getData(ChangesListView.EXACTLY_SELECTED_FILES_DATA_KEY)?.toList()

  return if (!exactlySelectedFiles.isNullOrEmpty()) exactlySelectedFiles
  else e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList() ?: emptyList()
}