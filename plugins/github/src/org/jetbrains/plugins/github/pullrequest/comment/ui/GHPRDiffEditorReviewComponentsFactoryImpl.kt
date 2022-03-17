// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.async.CompletableFutureUtil.successOnEdt
import com.intellij.collaboration.ui.codereview.comment.ReviewUIUtil
import com.intellij.diff.util.Side
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewComment
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewThread
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRCreateDiffCommentParametersHelper
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRSuggestedChangeHelper
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.JComponent

class GHPRDiffEditorReviewComponentsFactoryImpl
internal constructor(private val project: Project,
                     private val reviewDataProvider: GHPRReviewDataProvider,
                     private val avatarIconsProvider: GHAvatarIconsProvider,
                     private val createCommentParametersHelper: GHPRCreateDiffCommentParametersHelper,
                     private val suggestedChangeHelper: GHPRSuggestedChangeHelper,
                     private val currentUser: GHUser)
  : GHPRDiffEditorReviewComponentsFactory {

  override fun createThreadComponent(thread: GHPRReviewThreadModel): JComponent =
    GHPRReviewThreadComponent.create(project, thread,
                                     reviewDataProvider, avatarIconsProvider,
                                     suggestedChangeHelper,
                                     currentUser).apply {
      border = JBUI.Borders.empty(8, 8)
    }.let { ReviewUIUtil.createEditorInlayPanel(it) }

  override fun createSingleCommentComponent(side: Side, line: Int, startLine: Int, hideCallback: () -> Unit): JComponent {
    val textFieldModel = GHCommentTextFieldModel(project) {
      val filePath = createCommentParametersHelper.filePath
      if (line == startLine) {
        val commitSha = createCommentParametersHelper.commitSha
        val diffLine = createCommentParametersHelper.findPosition(side, line) ?: error("Can't determine comment position")
        reviewDataProvider.createReview(EmptyProgressIndicator(), GHPullRequestReviewEvent.COMMENT, null, commitSha,
                                        listOf(GHPullRequestDraftReviewComment(it, filePath, diffLine))).successOnEdt {
          hideCallback()
        }
      }
      else {
        reviewDataProvider.createThread(EmptyProgressIndicator(), null, it, line + 1, side, startLine + 1, filePath).successOnEdt {
          hideCallback()
        }
      }
    }

    return createCommentComponent(textFieldModel, GithubBundle.message("pull.request.diff.editor.review.comment"), hideCallback)
  }

  override fun createNewReviewCommentComponent(side: Side, line: Int, startLine: Int, hideCallback: () -> Unit): JComponent {
    val textFieldModel = GHCommentTextFieldModel(project) {
      val filePath = createCommentParametersHelper.filePath
      val commitSha = createCommentParametersHelper.commitSha
      if (line == startLine) {
        val diffLine = createCommentParametersHelper.findPosition(side, line) ?: error("Can't determine comment position")
        reviewDataProvider.createReview(EmptyProgressIndicator(), null, null, commitSha,
                                        listOf(GHPullRequestDraftReviewComment(it, filePath, diffLine))).successOnEdt {
          hideCallback()
        }
      }
      else {
        reviewDataProvider.createReview(EmptyProgressIndicator(), null, null, commitSha, null,
                                        listOf(GHPullRequestDraftReviewThread(it, line + 1, filePath, side, startLine + 1, side)))
          .successOnEdt {
            hideCallback()
          }
      }
    }

    return createCommentComponent(textFieldModel, GithubBundle.message("pull.request.diff.editor.review.start"), hideCallback)
  }

  override fun createReviewCommentComponent(reviewId: String, side: Side, line: Int, startLine: Int, hideCallback: () -> Unit): JComponent {
    val textFieldModel = GHCommentTextFieldModel(project) {
      val filePath = createCommentParametersHelper.filePath
      if (line == startLine) {
        val commitSha = createCommentParametersHelper.commitSha
        val diffLine = createCommentParametersHelper.findPosition(side, line) ?: error("Can't determine comment position")
        reviewDataProvider.addComment(EmptyProgressIndicator(), reviewId, it, commitSha, filePath, diffLine).successOnEdt {
          hideCallback()
        }
      }
      else {
        reviewDataProvider.createThread(EmptyProgressIndicator(), reviewId, it, line + 1, side, startLine + 1, filePath).successOnEdt {
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
  ): JComponent =
    GHCommentTextFieldFactory(textFieldModel).create(avatarIconsProvider, currentUser, actionName) {
      hideCallback()
    }.apply {
      border = JBUI.Borders.empty(8)
    }.let { ReviewUIUtil.createEditorInlayPanel(it) }
}