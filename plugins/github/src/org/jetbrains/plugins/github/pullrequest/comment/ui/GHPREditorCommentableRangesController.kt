// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.diff.util.LineRange
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.util.Disposer
import gnu.trove.TIntHashSet
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import javax.swing.JComponent

class GHPREditorCommentableRangesController(commentableRanges: SingleValueModel<List<LineRange>>,
                                            private val gutterIconRendererFactory: GHPRDiffEditorGutterIconRendererFactory,
                                            private val editor: EditorEx) {

  private val commentableLines = TIntHashSet()
  private val highlighters = mutableSetOf<RangeHighlighterEx>()

  init {
    val listenerDisposable = Disposer.newDisposable()
    editor.markupModel.addMarkupModelListener(listenerDisposable, object : MarkupModelListener {
      override fun beforeRemoved(highlighter: RangeHighlighterEx) {
        val iconRenderer = highlighter.gutterIconRenderer as? GHPRAddCommentGutterIconRenderer ?: return
        Disposer.dispose(iconRenderer)
        commentableLines.remove(iconRenderer.line)
        highlighters.remove(highlighter)
      }
    })
    val iconVisibilityController = IconVisibilityController()
    editor.addEditorMouseListener(iconVisibilityController)
    editor.addEditorMouseMotionListener(iconVisibilityController)

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
      highlighters.add(editor.markupModel
                         .addRangeHighlighterAndChangeAttributes(null, start, end, HighlighterLayer.LAST, HighlighterTargetArea.EXACT_RANGE,
                                                                 false) { highlighter ->
                           highlighter.gutterIconRenderer = gutterIconRendererFactory.createCommentRenderer(i)
                         })
    }
  }

  private inner class IconVisibilityController : EditorMouseListener, EditorMouseMotionListener {

    override fun mouseMoved(e: EditorMouseEvent) = doUpdate(e.editor, e.logicalPosition.line)
    override fun mouseExited(e: EditorMouseEvent) = doUpdate(e.editor, -1)

    private fun doUpdate(editor: Editor, line: Int) {
      highlighters.mapNotNull { it.gutterIconRenderer as? GHPRAddCommentGutterIconRenderer }.forEach {
        val visible = it.line == line
        val needUpdate = it.iconVisible != visible
        if (needUpdate) {
          it.iconVisible = visible
          val gutter = editor.gutter as JComponent
          val y = editor.logicalPositionToXY(LogicalPosition(it.line, 0)).y
          gutter.repaint(0, y, gutter.width, y + editor.lineHeight)
        }
      }
    }
  }
}