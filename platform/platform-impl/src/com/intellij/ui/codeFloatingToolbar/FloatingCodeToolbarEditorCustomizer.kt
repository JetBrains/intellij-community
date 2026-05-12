// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorCustomizer
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal class FloatingCodeToolbarEditorCustomizer : TextEditorCustomizer {
  override fun customize(textEditor: TextEditor, coroutineScope: CoroutineScope) {
    coroutineScope.launch {
      customizeAsync(textEditor, coroutineScope)
    }
  }

  private suspend fun customizeAsync(textEditor: TextEditor, coroutineScope: CoroutineScope) {
    val editor = textEditor.editor
    val project = editor.project ?: return
    val psiDocumentManager = project.serviceAsync<PsiDocumentManager>()
    val none = readAction {
      if (editor.isDisposed) return@readAction true

      val file = psiDocumentManager.getPsiFile(editor.document) ?: return@readAction true
      val languages = file.viewProvider.languages
      languages.none { language -> findActionGroupFor(language) != null }
    }
    if (none) {
      return
    }

    // The toolbar launches a never-completing `collectLatest` in `FloatingToolbar.init`,
    // so its scope must not be the short customization-launch coroutine.
    // It is cancelled by `FloatingToolbar.dispose()` via the Disposer chain below (IJPL-239466).
    val toolbarScope = coroutineScope.childScope("CodeFloatingToolbar")
    var registered = false
    try {
      readActionBlocking {
        if (editor.isDisposed) {
          return@readActionBlocking
        }

        val toolbar = CodeFloatingToolbar(editor = editor, coroutineScope = toolbarScope)
        registered = Disposer.tryRegister(textEditor, toolbar)
        if (!registered) {
          Disposer.dispose(toolbar)
        }
      }
    }
    finally {
      if (!registered) {
        toolbarScope.cancel()
      }
    }
  }
}