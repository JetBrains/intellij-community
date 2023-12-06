// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.vcs.log.impl.VcsProjectLog
import git4idea.i18n.GitBundle
import git4idea.log.GitLogIndexDataUtils

private class GitUseSharedLogAction : DumbAwareAction(GitBundle.messagePointer("vcs.log.use.log.index.data")) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project ?: disable(e) ?: return
    val vcsProjectLog = VcsProjectLog.getInstance(project)
    val data = vcsProjectLog.dataManager
    val isDataPackFull = data?.dataPack?.isFull ?: false

    val indexingFinished = GitLogIndexDataUtils.indexingFinished(data)
    if (isDataPackFull && indexingFinished) {
       disable<Unit>(e)
      return
    }

    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return

    val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
    val virtualFile = FileChooser.chooseFile(fileChooserDescriptor, project, null)
    if (virtualFile == null) return

    GitLogIndexDataUtils.extractLogDataFromArchive(project, virtualFile)
  }
}

internal class GitDumpLogIndexDataAction : DumbAwareAction(GitBundle.messagePointer("vcs.log.create.archive.with.log.index.data")) {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val project = e.project ?: disable(e) ?: return
    val vcsProjectLog = VcsProjectLog.getInstance(project)

    val data = vcsProjectLog.dataManager
    val isDataPackFull = data?.dataPack?.isFull ?: false

    val indexingFinished = GitLogIndexDataUtils.indexingFinished(data)
    if (isDataPackFull && indexingFinished) {
      e.presentation.isEnabledAndVisible = true
      return
    }
    disable<Unit>(e) ?: return
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return

    val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    val virtualFile = FileChooser.chooseFile(fileChooserDescriptor, project, null)
    if (virtualFile == null) return

    val outputArchiveDir = virtualFile.toNioPath()
    GitLogIndexDataUtils.createArchiveWithLogData(project, outputArchiveDir)
  }
}

private inline fun <reified T> disable(e: AnActionEvent): T? {
  e.presentation.isEnabledAndVisible = false
  return null
}