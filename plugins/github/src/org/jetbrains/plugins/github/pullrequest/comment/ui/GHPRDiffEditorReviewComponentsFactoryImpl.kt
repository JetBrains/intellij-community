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
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.avatars.CachingGithubAvatarIconsProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRCreateDiffCommentParametersHelper
import org.jetbrains.plugins.github.util.GithubUIUtil
import org.jetbrains.plugins.github.util.successOnEdt
import javax.swing.JComponent

class GHPRDiffEditorReviewComponentsFactoryImpl
internal constructor(private val reviewDataProvider: GHPRReviewDataProvider,
                     private val createCommentParametersHelper: GHPRCreateDiffCommentParametersHelper,
                     private val avatarIconsProviderFactory: CachingGithubAvatarIconsProvider.Factory,
                     private val currentUser: GHUser)
  : GHPRDiffEditorReviewComponentsFactory {

  override fun createThreadComponent(thread: GHPRReviewThreadModel): JComponent =
    wrapComponentUsingRoundedPanel { wrapper ->
      val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, wrapper)
      GHPRReviewThreadComponent.create(thread, reviewDataProvider, avatarIconsProvider, currentUser).apply {
        border = JBUI.Borders.empty(8, 8)
      }
    }

  override fun createSingleCommentComponent(side: Side, line: Int, hideCallback: () -> Unit): JComponent {
    val textFieldModel = GHSubmittableTextFieldModel {
      val commitSha = createCommentParametersHelper.commitSha
      val filePath = createCommentParametersHelper.filePath
      val diffLine = createCommentParametersHelper.findPosition(side, line) ?: error("Can't determine comment position")
      reviewDataProvider.createReview(EmptyProgressIndicator(), GHPullRequestReviewEvent.COMMENT, null, commitSha,
                                      listOf(GHPullRequestDraftReviewComment(it, filePath, diffLine))).successOnEdt {
        hideCallback()
      }
    }

    return createCommentComponent(textFieldModel, GithubBundle.message("pull.request.diff.editor.review.comment"), hideCallback)
  }

  override fun createNewReviewCommentComponent(side: Side, line: Int, hideCallback: () -> Unit): JComponent {
    val textFieldModel = GHSubmittableTextFieldModel {
      val commitSha = createCommentParametersHelper.commitSha
      val filePath = createCommentParametersHelper.filePath
      val diffLine = createCommentParametersHelper.findPosition(side, line) ?: error("Can't determine comment position")
      reviewDataProvider.createReview(EmptyProgressIndicator(), null, null, commitSha,
                                      listOf(GHPullRequestDraftReviewComment(it, filePath, diffLine))).successOnEdt {
        hideCallback()
      }
    }

    return createCommentComponent(textFieldModel, GithubBundle.message("pull.request.diff.editor.review.start"), hideCallback)
  }

  override fun createReviewCommentComponent(reviewId: String, side: Side, line: Int, hideCallback: () -> Unit): JComponent {
    val textFieldModel = GHSubmittableTextFieldModel {
      val commitSha = createCommentParametersHelper.commitSha
      val filePath = createCommentParametersHelper.filePath
      val diffLine = createCommentParametersHelper.findPosition(side, line) ?: error("Can't determine comment position")
      reviewDataProvider.addComment(EmptyProgressIndicator(), reviewId, it, commitSha, filePath, diffLine).successOnEdt {
        hideCallback()
      }
    }

    return createCommentComponent(textFieldModel, GithubBundle.message("pull.request.diff.editor.review.comment"), hideCallback)
  }

  private fun createCommentComponent(
    textFieldModel: GHSubmittableTextFieldModel,
    @NlsActions.ActionText actionName: String,
    hideCallback: () -> Unit
  ): JComponent = wrapComponentUsingRoundedPanel { wrapper ->
    val avatarIconsProvider = avatarIconsProviderFactory.create(GithubUIUtil.avatarSize, wrapper)

    GHSubmittableTextFieldFactory(textFieldModel).create(avatarIconsProvider, currentUser, actionName) {
      hideCallback()
    }.apply {
      border = JBUI.Borders.empty(8)
    }
  }
}