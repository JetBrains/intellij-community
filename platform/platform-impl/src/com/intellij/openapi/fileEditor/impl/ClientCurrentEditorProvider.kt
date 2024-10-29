// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.client.currentSession
import com.intellij.openapi.editor.ClientEditorManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.util.ui.UIUtil

private class ClientCurrentEditorProvider : CurrentEditorProvider {
  override fun getCurrentEditor(project: Project?): FileEditor? {
    if (project == null) {
      // fallback to search by focus
      return ClientEditorManager.getCurrentInstance().editorsSequence()
        .firstOrNull { UIUtil.hasFocus(it.contentComponent) }
        ?.let { TextEditorProvider.getInstance().getTextEditor(it) }
    }
    else {
      if (project.currentSession.isController) {
        // doesn't work in case of other remote clients.
        // check com.jetbrains.rdct.cwm.distributed.undo.EditorComplicatedUndoTest.testRedoBasicCommandAfterForeignTyping
        FocusBasedCurrentEditorProvider.getCurrentEditorEx()?.let { editor ->
          // GTW-6595
          return editor
        }
      }

      return FileEditorManager.getInstance(project).selectedEditor
    }
  }
}
