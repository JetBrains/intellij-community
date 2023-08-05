// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.impl.span
import kotlinx.coroutines.Dispatchers
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Experimental
/**
 * Executed only for psi-aware text editor.
 * Extension maybe implemented only by a core plugin.
 */
interface TextEditorInitializer {
  suspend fun init(project: Project, file: VirtualFile, document: Document, editorSupplier: suspend () -> EditorEx)
}

internal class HighlighterTextEditorInitializer : TextEditorInitializer {
  override suspend fun init(project: Project, file: VirtualFile, document: Document, editorSupplier: suspend () -> EditorEx) {
    val scheme = EditorColorsManager.getInstance().globalScheme
    val editorHighlighterFactory = EditorHighlighterFactory.getInstance()
    val highlighter = readAction {
      val highlighter = editorHighlighterFactory.createEditorHighlighter(file, scheme, project)
      highlighter.setText(document.immutableCharSequence)
      highlighter
    }

    val editor = editorSupplier()
    span("editor highlighter set", Dispatchers.EDT) {
      editor.settings.setLanguageSupplier { TextEditorImpl.getDocumentLanguage(editor) }
      editor.highlighter = highlighter
    }
  }
}