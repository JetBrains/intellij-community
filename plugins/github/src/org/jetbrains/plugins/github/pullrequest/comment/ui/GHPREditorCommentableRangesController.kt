// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.diff.util.LineRange
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.util.Disposer
import gnu.trove.TIntHashSet
import org.jetbrains.plugins.github.ui.util.SingleValueModel

class GHPREditorCommentableRangesController(commentableRanges: SingleValueModel<List<LineRange>>,
                                            private val gutterIconRendererFactory: GHPRDiffEditorGutterIconRendererFactory,
                                            private val editor: EditorEx) {

  private val commentableLines = TIntHashSet()

  init {
    val listenerDisposable = Disposer.newDisposable()
    editor.markupModel.addMarkupModelListener(listenerDisposable, object : MarkupModelListener {
      override fun beforeRemoved(highlighter: RangeHighlighterEx) {
        val iconRenderer = highlighter.gutterIconRenderer as? GHPRAddCommentGutterIconRenderer ?: return
        Disposer.dispose(iconRenderer)
        commentableLines.remove(iconRenderer.line)
      }
    })
    EditorUtil.disposeWithEditor(editor, listenerDisposable)

    commentableRanges.addAndInvokeValueChangedListener {
      for (range in commentableRanges.value) {
        markCommentableLines(range)
      }
    }
  }

  private fun markCommentableLines(range: LineRange) {
    for (i in range.start until range.end) {
      if (!commentableLines.add(i)) continue
      val start = editor.document.getLineStartOffset(i)
      val end = editor.document.getLineEndOffset(i)
      editor.markupModel
        .addRangeHighlighterAndChangeAttributes(start, end, HighlighterLayer.LAST, null, HighlighterTargetArea.EXACT_RANGE,
                                                false) { highlighter ->
          highlighter.gutterIconRenderer = gutterIconRendererFactory.createCommentRenderer(i)
        }
    }
  }
}