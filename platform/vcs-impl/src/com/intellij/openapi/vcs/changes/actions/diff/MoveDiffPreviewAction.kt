// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.editor.DiffContentVirtualFile
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys.VIRTUAL_FILES
import com.intellij.openapi.vcs.changes.VcsEditorTabFilesManager
import com.intellij.openapi.vcs.changes.VcsEditorTabFilesManager.Companion.OPENED_IN_NEW_WINDOW
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.JBIterable
import com.intellij.vcsUtil.VcsUtil

internal abstract class MoveDiffPreviewAction(private val openInNewWindow: Boolean) : DumbAwareAction() {

  abstract fun isEnabledAndVisible(project: Project, file: VirtualFile): Boolean

  override fun update(e: AnActionEvent) {
    val project = e.project
    val file = JBIterable.from(e.getData(VIRTUAL_FILES)).single()
    e.presentation.isEnabledAndVisible = project != null
                                         && file != null
                                         && file is DiffContentVirtualFile
                                         && isEnabledAndVisible(project, file)
  }



  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val diffPreviewFile = VcsUtil.getVirtualFiles(e).first()

    VcsEditorTabFilesManager.getInstance().openFile(project, diffPreviewFile, true, openInNewWindow, true)
  }
}

private fun VirtualFile.openedInNewWindow() = OPENED_IN_NEW_WINDOW.get(this, false)

internal class MoveDiffPreviewToEditorAction : MoveDiffPreviewAction(false) {
  override fun isEnabledAndVisible(project: Project, file: VirtualFile): Boolean = file.openedInNewWindow()
}

internal class MoveDiffPreviewToNewWindowAction : MoveDiffPreviewAction(true) {
  override fun isEnabledAndVisible(project: Project, file: VirtualFile): Boolean = !file.openedInNewWindow()
}
