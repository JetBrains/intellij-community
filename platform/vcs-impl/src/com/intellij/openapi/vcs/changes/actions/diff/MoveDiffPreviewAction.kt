// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys.VIRTUAL_FILE_STREAM
import com.intellij.openapi.vcs.changes.EditorDiffPreviewFilesManager
import com.intellij.openapi.vcs.changes.PreviewDiffVirtualFile
import com.intellij.util.containers.getIfSingle
import com.intellij.vcsUtil.VcsUtil

internal abstract class MoveDiffPreviewAction(private val openInNewWindow: Boolean) : DumbAwareAction() {

  abstract fun isEnabledAndVisible(project: Project): Boolean

  override fun update(e: AnActionEvent) {
    val project = e.project
    val file = e.getData(VIRTUAL_FILE_STREAM).getIfSingle()
    e.presentation.isEnabledAndVisible = project != null
                                         && isEnabledAndVisible(project)
                                         && file is PreviewDiffVirtualFile
  }



  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val diffPreviewFile = VcsUtil.getVirtualFiles(e).first() as PreviewDiffVirtualFile

    EditorDiffPreviewFilesManager.getInstance().openFile(project, diffPreviewFile, true, openInNewWindow, true)
  }
}

internal class MoveDiffPreviewToEditorAction : MoveDiffPreviewAction(false) {
  override fun isEnabledAndVisible(project: Project): Boolean = EditorDiffPreviewFilesManager.getInstance().shouldOpenInNewWindow
}

internal class MoveDiffPreviewToNewWindowAction : MoveDiffPreviewAction(true) {
  override fun isEnabledAndVisible(project: Project): Boolean = !EditorDiffPreviewFilesManager.getInstance().shouldOpenInNewWindow
}
