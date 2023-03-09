// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

interface DiffPreview {
  fun updatePreview(fromModelRefresh: Boolean) = Unit

  fun openPreview(requestFocus: Boolean): Boolean
  fun closePreview()

  /**
   * Allows overriding 'Show Diff' action availability and presentation
   */
  fun updateDiffAction(event: AnActionEvent) = Unit

  /**
   * Allows overriding 'Show Diff' action behavior.
   * For example, by using External Diff Tools when applicable.
   */
  fun performDiffAction(): Boolean = openPreview(true)

  companion object {
    @JvmStatic
    fun setPreviewVisible(preview: DiffPreview, value: Boolean) {
      if (value) {
        preview.openPreview(false)
      }
      else {
        preview.closePreview()
      }
    }

    /**
     * Close all file editors, including not from active splitters, associated with particular [previewFile]
     */
    @JvmStatic
    fun closePreviewFile(project: Project, previewFile: VirtualFile) {
      val editorManager = FileEditorManager.getInstance(project) as FileEditorManagerImpl
      editorManager.closeFile(previewFile, closeAllCopies = true, moveFocus = true)
    }
  }
}
