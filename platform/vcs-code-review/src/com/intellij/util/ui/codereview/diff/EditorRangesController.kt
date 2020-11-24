// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.codereview.diff

import com.intellij.diff.util.LineRange
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.util.Disposer
import it.unimi.dsi.fastutil.ints.IntOpenHashSet

open class EditorRangesController(private val gutterIconRendererFactory: DiffEditorGutterIconRendererFactory,
                                  private val editor: EditorEx) {
  private val commentableLines = IntOpenHashSet()
  private val highlighters: MutableSet<RangeHighlighterEx> = mutableSetOf()

  init {
    val listenerDisposable = Disposer.newDisposable()
    editor.markupModel.addMarkupModelListener(listenerDisposable, object : MarkupModelListener {
      override fun beforeRemoved(highlighter: RangeHighlighterEx) {
        val iconRenderer = highlighter.gutterIconRenderer as? AddCommentGutterIconRenderer ?: return
        Disposer.dispose(iconRenderer)
        commentableLines.remove(iconRenderer.line)
        highlighters.remove(highlighter)
      }
    })
    val iconVisibilityController = IconVisibilityController(highlighters)
    editor.addEditorMouseListener(iconVisibilityController)
    editor.addEditorMouseMotionListener(iconVisibilityController)

    EditorUtil.disposeWithEditor(editor, listenerDisposable)
  }

  protected fun markCommentableLines(range: LineRange) {
    for (i in range.start until range.end) {
      if (!commentableLines.add(i)) {
        continue
      }
      val start = editor.document.getLineStartOffset(i)
      val end = editor.document.getLineEndOffset(i)
      highlighters.add(editor.markupModel
                         .addRangeHighlighterAndChangeAttributes(null, start, end, HighlighterLayer.LAST, HighlighterTargetArea.EXACT_RANGE,
                                                                 false) { highlighter ->
                           highlighter.gutterIconRenderer = gutterIconRendererFactory.createCommentRenderer(i)
                         })
    }
  }
}