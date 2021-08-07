// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.editor.DiffContentVirtualFile
import com.intellij.diff.editor.DiffEditorTabFilesManager.Companion.isDiffOpenedInNewWindow
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys.VIRTUAL_FILES
import com.intellij.openapi.vcs.changes.VcsEditorTabFilesManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.JBIterable

internal abstract class MoveDiffPreviewAction(private val openInNewWindow: Boolean) : DumbAwareAction() {

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
  val file = JBIterable.from(getData(VIRTUAL_FILES)).single()
  if (file == null) return null

  if (file is DiffContentVirtualFile) return file

  // in case if Find Action executed, the first selected (focused) file will be a possible candidate
  val selectedFile = project?.let { FileEditorManager.getInstance(it).selectedFiles.firstOrNull() }

  return if (selectedFile is DiffContentVirtualFile) selectedFile else null
}

internal class MoveDiffPreviewToEditorAction : MoveDiffPreviewAction(false) {
  override fun isEnabledAndVisible(project: Project, file: VirtualFile): Boolean = isDiffOpenedInNewWindow(file)
}

internal class MoveDiffPreviewToNewWindowAction : MoveDiffPreviewAction(true) {
  override fun isEnabledAndVisible(project: Project, file: VirtualFile): Boolean = !isDiffOpenedInNewWindow(file)
}
