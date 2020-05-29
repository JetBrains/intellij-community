// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.SimpleEventListener
import org.jetbrains.plugins.github.util.GithubUIUtil
import javax.swing.JComponent

class GHPRDiffEditorGutterIconRendererFactoryImpl(private val reviewProcessModel: GHPRReviewProcessModel,
                                                  private val inlaysManager: EditorComponentInlaysManager,
                                                  private val componentFactory: GHPRDiffEditorReviewComponentsFactory,
                                                  private val lineLocationCalculator: (Int) -> Pair<Side, Int>?)
  : GHPRDiffEditorGutterIconRendererFactory {

  override fun createCommentRenderer(line: Int): GHPRAddCommentGutterIconRenderer = CreateCommentIconRenderer(line)

  private inner class CreateCommentIconRenderer(override val line: Int)
    : GHPRAddCommentGutterIconRenderer() {

    private var reviewState = ReviewState(false, null)

    private val reviewProcessListener = object : SimpleEventListener {
      override fun eventOccurred() {
        reviewState = ReviewState(reviewProcessModel.isActual, reviewProcessModel.pendingReview?.id)
      }
    }

    private var inlay: Pair<JComponent, Disposable>? = null

    init {
      reviewProcessModel.addAndInvokeChangesListener(reviewProcessListener)
    }

    override fun getClickAction(): DumbAwareAction? {
      if (inlay != null) return FocusInlayAction()
      val reviewId = reviewState.reviewId
      if (!reviewState.isDataActual || reviewId == null) return null
      return AddReviewCommentAction(line, reviewId)
    }

    private inner class FocusInlayAction : DumbAwareAction() {
      override fun actionPerformed(e: AnActionEvent) {
        if (inlay?.let { GithubUIUtil.focusPanel(it.first) } != null) return
      }
    }

    override fun getPopupMenuActions(): ActionGroup? {
      if (inlay != null) return null
      if (!reviewState.isDataActual || reviewState.reviewId != null) return null
      return DefaultActionGroup(StartReviewAction(line), AddSingleCommentAction(line))
    }

    private abstract inner class InlayAction(actionName: () -> String,
                                             private val editorLine: Int)
      : DumbAwareAction(actionName) {

      override fun actionPerformed(e: AnActionEvent) {
        if (inlay?.let { GithubUIUtil.focusPanel(it.first) } != null) return

        val (side, line) = lineLocationCalculator(editorLine) ?: return

        val hideCallback = {
          inlay?.let { Disposer.dispose(it.second) }
          inlay = null
        }
        val component = createComponent(side, line, hideCallback)
        val disposable = inlaysManager.insertAfter(editorLine, component) ?: return
        GithubUIUtil.focusPanel(component)
        inlay = component to disposable
      }

      protected abstract fun createComponent(side: Side, line: Int, hideCallback: () -> Unit): JComponent
    }

    private inner class AddSingleCommentAction(editorLine: Int)
      : InlayAction({ GithubBundle.message("pull.request.diff.editor.add.single.comment") }, editorLine) {

      override fun createComponent(side: Side, line: Int, hideCallback: () -> Unit) =
        componentFactory.createSingleCommentComponent(side, line, hideCallback)
    }

    private inner class StartReviewAction(editorLine: Int)
      : InlayAction({ GithubBundle.message("pull.request.diff.editor.review.with.comment") }, editorLine) {

      override fun createComponent(side: Side, line: Int, hideCallback: () -> Unit) =
        componentFactory.createNewReviewCommentComponent(side, line, hideCallback)
    }

    private inner class AddReviewCommentAction(editorLine: Int, private val reviewId: String)
      : InlayAction({ GithubBundle.message("pull.request.diff.editor.add.review.comment") }, editorLine) {

      override fun createComponent(side: Side, line: Int, hideCallback: () -> Unit) =
        componentFactory.createReviewCommentComponent(reviewId, side, line, hideCallback)
    }

    override fun dispose() {
      super.dispose()
      reviewProcessModel.removeChangesListener(reviewProcessListener)
    }

    override fun disposeInlay() {
      inlay?.second?.let { Disposer.dispose(it) }
    }
  }

  private data class ReviewState(val isDataActual: Boolean, val reviewId: String?)
}