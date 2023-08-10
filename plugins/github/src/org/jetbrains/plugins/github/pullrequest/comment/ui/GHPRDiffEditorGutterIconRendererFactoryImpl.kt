// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.SimpleEventListener
import com.intellij.collaboration.ui.codereview.diff.AddCommentGutterIconRenderer
import com.intellij.collaboration.ui.codereview.diff.DiffEditorGutterIconRendererFactory
import com.intellij.collaboration.ui.codereview.diff.EditorComponentInlaysManager
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.github.i18n.GithubBundle
import javax.swing.JComponent

class GHPRDiffEditorGutterIconRendererFactoryImpl(private val reviewProcessModel: GHPRReviewProcessModel,
                                                  private val inlaysManager: EditorComponentInlaysManager,
                                                  private val componentFactory: GHPRDiffEditorReviewComponentsFactory,
                                                  private val cumulative: Boolean,
                                                  private val lineLocationCalculator: (Int) -> GHPRCommentLocation?)
  : DiffEditorGutterIconRendererFactory {

  override fun createCommentRenderer(line: Int): AddCommentGutterIconRenderer = CreateCommentIconRenderer(line)

  private inner class CreateCommentIconRenderer(override val line: Int)
    : AddCommentGutterIconRenderer() {

    private var reviewState = ReviewState(false, null)

    private val reviewProcessListener = SimpleEventListener {
      reviewState = ReviewState(reviewProcessModel.isActual, reviewProcessModel.pendingReview?.id)
    }

    private var inlay: Pair<JComponent, Disposable>? = null

    init {
      reviewProcessModel.addAndInvokeChangesListener(reviewProcessListener)
    }

    override fun getShortcut(): ShortcutSet = getActiveKeymapShortcuts("Github.PullRequest.Diff.Comment.Create")

    override fun getClickAction(): DumbAwareAction? {
      if (inlay != null) return FocusInlayAction()
      val reviewId = reviewState.reviewId
      if (!reviewState.isDataActual || reviewId == null) return null

      return AddReviewCommentAction(line, reviewId, isMultiline(line))
    }

    private inner class FocusInlayAction : DumbAwareAction() {
      override fun actionPerformed(e: AnActionEvent) {
        if (inlay?.let {
            CollaborationToolsUIUtil.focusPanel(it.first)
          } != null) return
      }
    }

    override fun getPopupMenuActions(): ActionGroup? {
      if (inlay != null) return null
      if (!reviewState.isDataActual || reviewState.reviewId != null) return null

      val multiline = isMultiline(line)
      return DefaultActionGroup(StartReviewAction(line, multiline), AddSingleCommentAction(line, multiline))
    }

    private fun isMultiline(line: Int) = cumulative &&
                                         (lineLocationCalculator(line)
                                          ?: GHPRCommentLocation(Side.RIGHT, line, line, line)).let { it.line != it.startLine }

    private abstract inner class InlayAction(actionName: () -> String,
                                             private val editorLine: Int)
      : DumbAwareAction(actionName) {

      override fun actionPerformed(e: AnActionEvent) {
        if (inlay?.let {
            CollaborationToolsUIUtil.focusPanel(it.first)
          } != null) return

        val (side, line, startLine, realEditorLine) = lineLocationCalculator(editorLine) ?: return

        val hideCallback = {
          inlay?.let { Disposer.dispose(it.second) }
          inlay = null
        }

        val component = if (isMultiline(editorLine))
          createComponent(side, line, startLine, hideCallback)
        else
          createComponent(side, line, line, hideCallback)

        val disposable = inlaysManager.insertAfter(realEditorLine, component) ?: return
        CollaborationToolsUIUtil.focusPanel(component)
        inlay = component to disposable
      }

      protected abstract fun createComponent(side: Side, line: Int, startLine: Int = line, hideCallback: () -> Unit): JComponent
    }

    private inner class AddSingleCommentAction(editorLine: Int, isMultiline: Boolean = false)
      : InlayAction({
                      if (isMultiline) GithubBundle.message("pull.request.diff.editor.add.multiline.comment")
                      else GithubBundle.message("pull.request.diff.editor.add.single.comment")
                    }, editorLine) {

      override fun createComponent(side: Side, line: Int, startLine: Int, hideCallback: () -> Unit) =
        componentFactory.createSingleCommentComponent(side, line, startLine, hideCallback)
    }

    private inner class StartReviewAction(editorLine: Int, isMultiline: Boolean = false)
      : InlayAction({
                      if (isMultiline) GithubBundle.message("pull.request.diff.editor.start.review.with.multiline.comment")
                      else GithubBundle.message("pull.request.diff.editor.review.with.comment")
                    }, editorLine) {

      override fun createComponent(side: Side, line: Int, startLine: Int, hideCallback: () -> Unit) =
        componentFactory.createNewReviewCommentComponent(side, line, startLine, hideCallback)
    }

    private inner class AddReviewCommentAction(editorLine: Int, private val reviewId: String, isMultiline: Boolean = false)
      : InlayAction({
                      if (isMultiline) GithubBundle.message("pull.request.diff.editor.add.multiline.review.comment")
                      else GithubBundle.message("pull.request.diff.editor.add.review.comment")
                    }, editorLine) {

      override fun createComponent(side: Side, line: Int, startLine: Int, hideCallback: () -> Unit) =
        componentFactory.createReviewCommentComponent(reviewId, side, line, startLine, hideCallback)
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

data class GHPRCommentLocation(val side: Side, val line: Int, val startLine: Int = line, val editorLine: Int = line)
