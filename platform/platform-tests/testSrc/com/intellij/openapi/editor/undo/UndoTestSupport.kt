// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.undo

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider
import com.intellij.openapi.project.Project

internal fun undoManager(project: Project): UndoManagerImpl {
  return UndoManager.getInstance(project) as UndoManagerImpl
}

/**
 * Runs [task] while [editor] is the current editor of this undo manager.
 */
internal fun UndoManagerImpl.withCurrentEditor(editor: FileEditor, task: () -> Unit) {
  val provider = object : CurrentEditorProvider {
    override fun getCurrentEditor(project: Project?): FileEditor = editor
  }
  setOverriddenEditorProvider(provider)
  try {
    task()
  } finally {
    setOverriddenEditorProvider(null)
  }
}

/**
 * Inserts a space at the start of [document] in a command, and then runs [afterChange] in the same command.
 */
internal fun typeSpace(project: Project, document: Document, afterChange: () -> Unit = {}) {
  val command = Runnable {
    ApplicationManager.getApplication().runWriteAction {
      document.insertString(0, " ")
    }
    afterChange()
  }
  CommandProcessor.getInstance().executeCommand(project, command, "type", null)
}
