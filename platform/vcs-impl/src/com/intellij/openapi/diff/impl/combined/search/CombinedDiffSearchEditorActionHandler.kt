// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.combined.search

import com.intellij.diff.tools.combined.CombinedDiffBaseEditorWithSelectionHandler
import com.intellij.diff.tools.combined.CombinedDiffViewer
import com.intellij.diff.tools.combined.search.CombinedDiffSearchProvider
import com.intellij.diff.util.DiffUtil
import com.intellij.find.SearchSession
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler

private class CombinedDiffSearchEditorActionHandler(original: EditorActionHandler) : CombinedDiffBaseEditorWithSelectionHandler(original) {

  override fun doExecute(combined: CombinedDiffViewer, editor: Editor, caret: Caret?, dc: DataContext?) {
    val project = dc?.getData(CommonDataKeys.PROJECT) ?: return
    project.service<CombinedDiffSearchProvider>().installSearch(combined)
  }
}

private class CombinedDiffReplaceEditorActionHandler(private val original: EditorActionHandler) : CombinedDiffBaseEditorWithSelectionHandler(original) {

  override fun doExecute(combined: CombinedDiffViewer, editor: Editor, caret: Caret?, dc: DataContext?) {
    if (DiffUtil.isEditable(editor)) {
      //open single editor replace, global replace not supported
      original.execute(editor, caret, dc)
      return
    }

    val project = dc?.getData(CommonDataKeys.PROJECT) ?: return
    project.service<CombinedDiffSearchProvider>().installSearch(combined)
  }
}

private class SearchNextHandler(original: EditorActionHandler) : CombinedDiffBaseEditorWithSelectionHandler(original) {
  override fun doExecute(combined: CombinedDiffViewer, editor: Editor, caret: Caret?, dc: DataContext?) {
    if (dc == null) return

    invokeGoToOccurence(true, dc)
  }
}

private class SearchPreviousHandler(original: EditorActionHandler) : CombinedDiffBaseEditorWithSelectionHandler(original) {
  override fun doExecute(combined: CombinedDiffViewer, editor: Editor, caret: Caret?, dc: DataContext?) {
    if (dc == null) return

    invokeGoToOccurence(false, dc)
  }
}

private fun invokeGoToOccurence(forward: Boolean, handlerContext: DataContext) {
  val session = handlerContext.getData(SearchSession.KEY) ?: return

  if (forward) session.searchForward() else session.searchBackward()
}
