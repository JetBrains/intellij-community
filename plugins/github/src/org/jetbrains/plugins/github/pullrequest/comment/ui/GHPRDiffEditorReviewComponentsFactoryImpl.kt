// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.diff.util.Side
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.NlsActions
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.codereview.comment.wrapComponentUsingRoundedPanel
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewComment
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewThread
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRCreateDiffCommentParametersHelper
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.util.successOnEdt
import javax.swing.JComponent

class GHPRDiffEditorReviewComponentsFactoryImpl
internal constructor(private val reviewDataProvider: GHPRReviewDataProvider,
                     private val createCommentParametersHelper: GHPRCreateDiffCommentParametersHelper,
                     private val avatarIconsProvider: GHAvatarIconsProvider,
                     private val currentUser: GHUser)
  : GHPRDiffEditorReviewComponentsFactory {

  override fun createThreadComponent(thread: GHPRReviewThreadModel): JComponent =
    GHPRReviewThreadComponent.create(thread, reviewDataProvider, avatarIconsProvider, currentUser).apply {
      border = JBUI.Borders.empty(8, 8)
    }.let(::wrapComponentUsingRoundedPanel)

  override fun createSingleCommentComponent(side: Side, line: Int, startLine: Int, hideCallback: () -> Unit): JComponent {
    val textFieldModel = GHSubmittableTextFieldModel {
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
    val textFieldModel = GHSubmittableTextFieldModel {
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
    val textFieldModel = GHSubmittableTextFieldModel {
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
    textFieldModel: GHSubmittableTextFieldModel,
    @NlsActions.ActionText actionName: String,
    hideCallback: () -> Unit
  ): JComponent =
    GHSubmittableTextFieldFactory(textFieldModel).create(avatarIconsProvider, currentUser, actionName) {
      hideCallback()
    }.apply {
      border = JBUI.Borders.empty(8)
    }.let(::wrapComponentUsingRoundedPanel)
}
