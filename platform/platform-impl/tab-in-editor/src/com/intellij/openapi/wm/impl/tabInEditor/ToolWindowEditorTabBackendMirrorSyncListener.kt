// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileOpenedSyncListener
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Binds the editor of a tool window editor tab to its backend mirror once the editor composite has been opened.
 *
 * When a tab is moved from the tool window to the editor, the binding cannot be established earlier.
 * [ToolWindowEditorTabSession] updates the file name and icon before the file is opened, while
 * [syncToolWindowEditorTabWithBackendMirror] still cannot resolve the corresponding editor and provider.
 * As a result, the backend mirror initially remains unbound to an editor.
 */
internal class ToolWindowEditorTabBackendMirrorSyncListener(private val project: Project) : FileOpenedSyncListener {
  override fun fileOpenedSync(
    source: FileEditorManager,
    file: VirtualFile,
    editorsWithProviders: List<FileEditorWithProvider>,
  ) {
    if (file !is ToolWindowEditorTabFile) return

    val editorWithProvider = editorsWithProviders.firstOrNull { it.fileEditor is ToolWindowEditorTabFileEditor } ?: return
    ToolWindowEditorTabManager.getInstance(project).scheduleBackendMirrorSync(file, editorWithProvider)
  }
}
