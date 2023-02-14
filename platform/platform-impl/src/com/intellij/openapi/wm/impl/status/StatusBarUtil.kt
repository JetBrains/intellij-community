// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.ui.UIUtil
import java.awt.Component

object StatusBarUtil {
  internal fun getStatusBar(component: Component): StatusBar? {
    var parent: Component? = component
    while (parent != null) {
      if (parent is IdeFrame) {
        return parent.statusBar
      }
      parent = parent.parent
    }
    return null
  }

  @JvmStatic
  fun getCurrentTextEditor(statusBar: StatusBar?): Editor? {
    val fileEditor = getCurrentFileEditor(statusBar) as? TextEditor ?: return null
    val editor = fileEditor.editor
    return if (ensureValidEditorFile(editor, fileEditor) && UIUtil.isShowing(editor.component)) editor else null
  }

  /**
   * Finds the current file editor.
   */
  @JvmStatic
  fun getCurrentFileEditor(statusBar: StatusBar?): FileEditor? {
    return statusBar?.currentEditor?.invoke()
  }

  @JvmStatic
  fun setStatusBarInfo(project: Project, message: @NlsContexts.StatusBarText String) {
    WindowManager.getInstance().getStatusBar(project)?.info = message
  }

  private fun ensureValidEditorFile(editor: Editor, fileEditor: FileEditor?): Boolean {
    val document = editor.document
    val file = FileDocumentManager.getInstance().getFile(document)
    if (file == null || file.isValid) {
      return true
    }

    val cachedDocument = FileDocumentManager.getInstance().getCachedDocument(file)
    val project = editor.project
    val fileIsOpen = if (project == null) null else FileEditorManager.getInstance(project).isFileOpen(file)
    Logger.getInstance(StatusBar::class.java).error(
      "Returned editor for invalid file: " + editor +
      "; disposed=" + editor.isDisposed +
      (if (fileEditor == null) "" else "; fileEditor=" + fileEditor + "; fileEditor.valid=" + fileEditor.isValid) +
      "; file " + file.javaClass +
      "; cached document exists: " + (cachedDocument != null) +
      "; same as document: " + (cachedDocument === document) +
      "; file is open: " + fileIsOpen
    )
    return false
  }
}