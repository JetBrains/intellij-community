// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorCustomizer
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.coroutineScope

private class FloatingCodeToolbarEditorCustomizer: TextEditorCustomizer {
  override suspend fun execute(textEditor: TextEditor) {
    val editor = textEditor.editor
    val psiDocumentManager = (editor.project ?: return).serviceAsync<PsiDocumentManager>()
    val none = readAction {
      if (editor.isDisposed) return@readAction true

      val file = psiDocumentManager.getPsiFile(editor.document) ?: return@readAction true
      val languages = file.viewProvider.languages
      languages.none { language -> findActionGroupFor(language) != null }
    }
    if (none) {
      return
    }

    coroutineScope {
      readActionBlocking {
        if (editor.isDisposed) return@readActionBlocking

        val toolbar = CodeFloatingToolbar(editor = editor, coroutineScope = this)
        Disposer.register(textEditor, toolbar)
      }
    }
  }
}