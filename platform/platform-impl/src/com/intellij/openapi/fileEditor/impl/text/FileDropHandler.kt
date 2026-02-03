// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorDropHandler
import com.intellij.openapi.editor.FileDropManager
import com.intellij.openapi.editor.containsFileDropTargets
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.project.Project
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

@Deprecated("Use com.intellij.openapi.fileEditor.impl.text.FileDropManager directly")
open class FileDropHandler(myEditor: Editor?) : EditorDropHandler {
  private val impl = FileEditorDropHandler(myEditor)

  override fun canHandleDrop(transferFlavors: Array<DataFlavor>): Boolean = impl.canHandleDrop(transferFlavors)

  override fun handleDrop(t: Transferable, project: Project?, editorWindowCandidate: EditorWindow?) {
    impl.handleDrop(t, project, editorWindowCandidate)
  }
}

// implementation detail, for platform needs only, must not be exposed as API
internal class FileEditorDropHandler(private val editor: Editor?) : EditorDropHandler {
  override fun canHandleDrop(transferFlavors: Array<DataFlavor>): Boolean {
    return containsFileDropTargets(transferFlavors)
  }

  override fun handleDrop(t: Transferable, project: Project?, editorWindowCandidate: EditorWindow?) {
    project ?: return

    project.service<FileDropManager>().scheduleDrop(t, editor, editorWindowCandidate)
  }
}
