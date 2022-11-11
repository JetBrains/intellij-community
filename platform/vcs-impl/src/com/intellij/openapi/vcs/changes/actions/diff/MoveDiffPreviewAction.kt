// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.editor.DiffContentVirtualFile
import com.intellij.diff.editor.DiffEditorTabFilesManager.Companion.isDiffOpenedInNewWindow
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys.VIRTUAL_FILES
import com.intellij.openapi.vcs.changes.VcsEditorTabFilesManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.JBIterable

internal abstract class MoveDiffPreviewAction(private val openInNewWindow: Boolean) : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  abstract fun isEnabledAndVisible(project: Project, file: VirtualFile): Boolean

  override fun update(e: AnActionEvent) {
    val project = e.project
    val diffPreviewFile = e.findDiffPreviewFile()
    e.presentation.isEnabledAndVisible = project != null &&
                                         diffPreviewFile != null &&
                                         isEnabledAndVisible(project, diffPreviewFile)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val diffPreviewFile = e.findDiffPreviewFile()!!

    VcsEditorTabFilesManager.getInstance().openFile(project, diffPreviewFile, true, openInNewWindow, true)
  }
}

private fun AnActionEvent.findDiffPreviewFile(): VirtualFile? {
  return JBIterable.empty<VirtualFile>()
    .append(JBIterable.from(getData(VIRTUAL_FILES)).single())
    .append(getData(PlatformDataKeys.FILE_EDITOR)?.file)
    // for 'Find Action' context, the first selected file will be a possible candidate
    .append(getData(PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR)?.file)
    .filter { it is DiffContentVirtualFile }
    .first()
}

internal class MoveDiffPreviewToEditorAction : MoveDiffPreviewAction(false) {
  override fun isEnabledAndVisible(project: Project, file: VirtualFile): Boolean = isDiffOpenedInNewWindow(file)
}

internal class MoveDiffPreviewToNewWindowAction : MoveDiffPreviewAction(true) {
  override fun isEnabledAndVisible(project: Project, file: VirtualFile): Boolean = !isDiffOpenedInNewWindow(file)
}
