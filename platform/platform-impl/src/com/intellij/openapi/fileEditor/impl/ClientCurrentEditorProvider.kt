// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.client.ClientAppSession
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.ClientEditorManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.util.ui.UIUtil

internal class ClientCurrentEditorProvider(val session: ClientAppSession) : CurrentEditorProvider {
  override fun getCurrentEditor(project: Project?): FileEditor? {
    if (session.isDisposed) {
      logger<ClientCurrentEditorProvider>().warn("The client (${ClientId.current}) session has expired.", Throwable())
      return null
    }

    if (project == null) {
      // fallback to search by focus
      return session.service<ClientEditorManager>().editorsSequence()
        .firstOrNull { UIUtil.hasFocus(it.contentComponent) }
        ?.let { TextEditorProvider.getInstance().getTextEditor(it) }
    }
    else {
      if (session.isController) {
        // GTW-6595 doesn't work in case of other remote clients.
        // check com.jetbrains.rdct.cwm.distributed.undo.EditorComplicatedUndoTest.testRedoBasicCommandAfterForeignTyping
        FocusBasedCurrentEditorProvider.getCurrentEditorEx()?.let { editor ->
          return editor
        }
      }

      return FileEditorManager.getInstance(project).selectedEditor
    }
  }
}
