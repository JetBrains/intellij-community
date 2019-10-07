// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import org.jetbrains.plugins.github.pullrequest.comment.viewer.GHPRCreateDiffCommentIconRenderer
import org.jetbrains.plugins.github.pullrequest.data.GHPRReviewServiceAdapter
import org.jetbrains.plugins.github.pullrequest.data.model.GHPRDiffRangeMapping
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

class GHPREditorReviewThreadsController(threadMap: GHPREditorReviewThreadsModel,
                                        private val commentableRanges: SingleValueModel<List<GHPRDiffRangeMapping>>,
                                        private val reviewService: GHPRReviewServiceAdapter,
                                        private val componentFactory: GHPREditorReviewCommentsComponentFactory,
                                        private val editor: EditorEx) {
  private val inlaysManager = EditorComponentInlaysManager(editor as EditorImpl)

  private val inlayByThread = mutableMapOf<GHPRReviewThreadModel, Inlay<*>>()

  init {
    commentableRanges.addValueChangedListener { updateCommentableRanges() }
    updateCommentableRanges()
  }

  private fun updateCommentableRanges() {
    for (range in commentableRanges.value) {
      markCommentableLines(range)
    }
  }

  private fun markCommentableLines(mapping: GHPRDiffRangeMapping) {
    for (i in mapping.start until mapping.end) {
      val start = editor.document.getLineStartOffset(i)
      val end = editor.document.getLineEndOffset(i)
      editor.markupModel
        .addRangeHighlighterAndChangeAttributes(start, end, HighlighterLayer.LAST, null, HighlighterTargetArea.EXACT_RANGE,
                                                false) { highlighter ->
          highlighter.gutterIconRenderer = GHPRCreateDiffCommentIconRenderer(i, object : DumbAwareAction() {
            private var inlay: Inlay<*>? = null

            override fun actionPerformed(e: AnActionEvent) {
              if (inlay?.let { focusInlay(it) } != null) return

              val component = componentFactory.createCommentComponent(reviewService,
                                                                      mapping.commitSha, mapping.filePath,
                                                                      i + mapping.offset) {
                inlay?.let { Disposer.dispose(it) }
              }
              val newInlay = inlaysManager.insertAfter(i, component) ?: return
              component.revalidate()
              component.repaint()
              //TODO: replace with focus listeners
              component.registerKeyboardAction({
                                                 newInlay.let {
                                                   Disposer.dispose(it)
                                                   inlay = null
                                                 }
                                               },
                                               KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                               JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
              focusInlay(newInlay)

              inlay = newInlay
            }

            private fun focusInlay(inlay: Inlay<*>) {
              val component = inlaysManager.findComponent(inlay) ?: return
              val focusManager = IdeFocusManager.findInstanceByComponent(component)
              val toFocus = focusManager.getFocusTargetFor(component) ?: return
              focusManager.doWhenFocusSettlesDown { focusManager.requestFocus(toFocus, true) }
            }
          })
        }
    }
  }

  init {
    for ((line, threads) in threadMap.threadsByLine) {
      for (thread in threads) {
        val inlay = inlaysManager.insertAfter(line, componentFactory.createThreadComponent(reviewService, thread)) ?: break
        inlayByThread[thread] = inlay
      }
    }

    threadMap.addChangesListener(object : GHPREditorReviewThreadsModel.ChangesListener {
      override fun threadsAdded(line: Int, threads: List<GHPRReviewThreadModel>) {
        for (thread in threads) {
          val inlay = inlaysManager.insertAfter(line, componentFactory.createThreadComponent(reviewService, thread)) ?: break
          inlayByThread[thread] = inlay
        }
      }

      override fun threadsRemoved(line: Int, threads: List<GHPRReviewThreadModel>) {
        for (thread in threads) {
          val inlay = inlayByThread.remove(thread) ?: continue
          Disposer.dispose(inlay)
        }
      }
    })
  }
}