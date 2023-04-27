// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.diff.util.Side
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.util.ui.JBUI
import git4idea.changes.GitTextFilePatchWithHistory
import git4idea.changes.filePath
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewThread
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.JComponent

class GHPRDiffEditorReviewComponentsFactoryImpl
internal constructor(private val project: Project,
                     private val reviewDataProvider: GHPRReviewDataProvider,
                     private val htmlImageLoader: AsyncHtmlImageLoader,
                     private val avatarIconsProvider: GHAvatarIconsProvider,
                     private val diffData: GitTextFilePatchWithHistory,
                     private val suggestedChangeHelper: GHPRSuggestedChangeHelper,
                     private val ghostUser: GHUser,
                     private val currentUser: GHUser)
  : GHPRDiffEditorReviewComponentsFactory {

  override fun createThreadComponent(thread: GHPRReviewThreadModel): JComponent =
    GHPRReviewThreadComponent.createForInlay(project, thread, reviewDataProvider,
                                             htmlImageLoader, avatarIconsProvider, suggestedChangeHelper,
                                             ghostUser, currentUser).apply {
      border = JBUI.Borders.empty(CodeReviewCommentUIUtil.INLAY_PADDING - GHPRReviewThreadComponent.INLAY_COMPONENT_TYPE.paddingInsets.top,
                                  0,
                                  CodeReviewCommentUIUtil.INLAY_PADDING - GHPRReviewThreadComponent.INLAY_COMPONENT_TYPE.paddingInsets.bottom,
                                  0)
    }.let { CodeReviewCommentUIUtil.createEditorInlayPanel(it) }

  override fun createSingleCommentComponent(side: Side, line: Int, startLine: Int, hideCallback: () -> Unit): JComponent {
    val textFieldModel = GHCommentTextFieldModel(project) {
      val commitSha = diffData.patch.afterVersionId!!
      val filePath = diffData.patch.filePath

      val thread = if (line == startLine) {
        GHPullRequestDraftReviewThread(it, line + 1, filePath, side, null, null)
      }
      else {
        GHPullRequestDraftReviewThread(it, line + 1, filePath, side, startLine + 1, side)
      }

      reviewDataProvider.createReview(EmptyProgressIndicator(), GHPullRequestReviewEvent.COMMENT, null, commitSha, listOf(thread))
        .successOnEdt {
          hideCallback()
        }
    }

    return createCommentComponent(textFieldModel, GithubBundle.message("pull.request.diff.editor.review.comment"), hideCallback)
  }

  override fun createNewReviewCommentComponent(side: Side, line: Int, startLine: Int, hideCallback: () -> Unit): JComponent {
    val textFieldModel = GHCommentTextFieldModel(project) {
      val commitSha = diffData.patch.afterVersionId!!
      val filePath = diffData.patch.filePath

      val thread = if (line == startLine) {
        GHPullRequestDraftReviewThread(it, line + 1, filePath, side, null, null)
      }
      else {
        GHPullRequestDraftReviewThread(it, line + 1, filePath, side, startLine + 1, side)
      }

      reviewDataProvider.createReview(EmptyProgressIndicator(), null, null, commitSha, listOf(thread)).successOnEdt {
        hideCallback()
      }
    }

    return createCommentComponent(textFieldModel, GithubBundle.message("pull.request.diff.editor.review.start"), hideCallback)
  }

  override fun createReviewCommentComponent(reviewId: String, side: Side, line: Int, startLine: Int, hideCallback: () -> Unit): JComponent {
    val textFieldModel = GHCommentTextFieldModel(project) {
      val filePath = diffData.patch.filePath
      if (diffData.isCumulative) {
        reviewDataProvider.createThread(EmptyProgressIndicator(), reviewId, it, line + 1, side, startLine + 1, filePath).successOnEdt {
          hideCallback()
        }
      }
      else {
        val commitSha = diffData.patch.afterVersionId!!
        reviewDataProvider.addComment(EmptyProgressIndicator(), reviewId, it, commitSha, filePath, side, line).successOnEdt {
          hideCallback()
        }
      }
    }

    return createCommentComponent(textFieldModel, GithubBundle.message("pull.request.diff.editor.review.comment"), hideCallback)
  }

  private fun createCommentComponent(
    textFieldModel: GHCommentTextFieldModel,
    @NlsActions.ActionText actionName: String,
    hideCallback: () -> Unit
  ): JComponent {
    val submitShortcutText = CommentInputActionsComponentFactory.submitShortcutText

    val cancelAction = swingAction("") {
      hideCallback()
    }
    val submitAction = swingAction(actionName) {
      textFieldModel.submit()
    }
    textFieldModel.isBusyValue.addAndInvokeListener {
      submitAction.isEnabled = !it
    }

    val actions = CommentInputActionsComponentFactory.Config(
      primaryAction = MutableStateFlow(submitAction),
      cancelAction = MutableStateFlow(cancelAction),
      submitHint = MutableStateFlow(GithubBundle.message("pull.request.comment.hint", submitShortcutText))
    )

    return GHCommentTextFieldFactory(textFieldModel).create(
      actions,
      CommentTextFieldFactory.IconConfig.of(CodeReviewChatItemUIUtil.ComponentType.COMPACT, avatarIconsProvider, currentUser.avatarUrl)
    ).apply {
      border = JBUI.Borders.empty(8)
    }.let { CodeReviewCommentUIUtil.createEditorInlayPanel(it) }
  }
}