// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.ScrollingModel
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.editor.ex.*
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
internal class EditorModelImpl(private val editor: EditorImpl) : EditorModel {
  override fun getDocument(): DocumentEx = editor.document
  override fun getEditorMarkupModel(): MarkupModelEx = editor.markupModel
  override fun getDocumentMarkupModel(): MarkupModelEx = editor.filteredDocumentMarkupModel
  override fun getHighlighter(): EditorHighlighter = editor.highlighter
  override fun getInlayModel(): InlayModelEx = editor.inlayModel
  override fun getFoldingModel(): FoldingModelEx = editor.foldingModel
  override fun getSoftWrapModel(): SoftWrapModelEx = editor.softWrapModel
  override fun getCaretModel(): CaretModel = editor.caretModel
  override fun getSelectionModel(): SelectionModel = editor.selectionModel
  override fun getScrollingModel(): ScrollingModel = editor.scrollingModel
  override fun getFocusModel(): FocusModeModel = editor.focusModeModel
  override fun isAd(): Boolean = false
  override fun dispose() {}
}
