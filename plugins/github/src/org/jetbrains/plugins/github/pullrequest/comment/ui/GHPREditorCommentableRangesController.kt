// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import gnu.trove.TIntHashSet
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRCreateDiffCommentIconRenderer
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.GithubUIUtil
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

class GHPREditorCommentableRangesController(commentableRanges: SingleValueModel<List<LineRange>>,
                                            private val componentFactory: GHPRDiffEditorReviewComponentsFactory,
                                            private val inlaysManager: EditorComponentInlaysManager,
                                            private val lineLocationCalculator: (Int) -> Pair<Side, Int>?) {

  private val editor = inlaysManager.editor
  private val commentableLines = TIntHashSet()

  init {
    val listenerDisposable = Disposer.newDisposable()
    editor.markupModel.addMarkupModelListener(listenerDisposable, object : MarkupModelListener {
      override fun beforeRemoved(highlighter: RangeHighlighterEx) {
        val iconRenderer = highlighter.gutterIconRenderer as? GHPRCreateDiffCommentIconRenderer ?: return
        commentableLines.remove(iconRenderer.line)
      }
    })
    EditorUtil.disposeWithEditor(editor, listenerDisposable)

    for (range in commentableRanges.value) {
      markCommentableLines(range)
    }
    commentableRanges.addValueChangedListener {
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
          highlighter.gutterIconRenderer = GHPRCreateDiffCommentIconRenderer(i, object : DumbAwareAction() {
            private var inlay: Pair<JComponent, Disposable>? = null

            override fun actionPerformed(e: AnActionEvent) {
              if (inlay?.let { GithubUIUtil.focusPanel(it.first) } != null) return

              val (side, line) = lineLocationCalculator(i) ?: return

              val component =
                componentFactory.createCommentComponent(side, line) {
                  inlay?.let { Disposer.dispose(it.second) }
                  inlay = null
                }
              val disposable = inlaysManager.insertAfter(i, component) ?: return
              val newInlay = component to disposable
              //TODO: replace with focus listeners
              component.registerKeyboardAction({
                                                 disposable.let {
                                                   Disposer.dispose(it)
                                                   inlay = null
                                                 }
                                               },
                                               KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                               JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
              GithubUIUtil.focusPanel(component)

              inlay = newInlay
            }
          })
        }
    }
  }
}