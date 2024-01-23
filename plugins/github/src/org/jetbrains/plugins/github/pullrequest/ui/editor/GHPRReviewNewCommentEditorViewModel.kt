// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.computationStateIn
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModel
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModelBase
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.filePath
import com.intellij.collaboration.util.getOrNull
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.asSafely
import com.intellij.vcsUtil.VcsFileUtil.relativePath
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.future.await
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewThread
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.createPendingReviewRequestsFlow
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentPosition
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModel.SubmitAction
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider

interface GHPRReviewNewCommentEditorViewModel : CodeReviewSubmittableTextViewModel {
  val currentUser: GHActor
  val avatarIconsProvider: GHAvatarIconsProvider
  val submitActions: StateFlow<List<SubmitAction>>

  fun cancel()

  sealed interface SubmitAction : () -> Unit {
    fun interface CreateSingleComment : SubmitAction
    fun interface CreateReview : SubmitAction
    fun interface CreateReviewComment : SubmitAction
  }
}

internal class GHPRReviewNewCommentEditorViewModelImpl(
  override val project: Project,
  parentCs: CoroutineScope,
  private val reviewDataProvider: GHPRReviewDataProvider,
  private val repository: GitRepository,
  override val currentUser: GHActor,
  override val avatarIconsProvider: GHAvatarIconsProvider,
  private val position: GHPRReviewCommentPosition,
  private val onCancel: () -> Unit
) : CodeReviewSubmittableTextViewModelBase(project, parentCs, ""),
    GHPRReviewNewCommentEditorViewModel {
  private val settings = GithubPullRequestsProjectUISettings.getInstance(project)

  private val pendingReviewState: StateFlow<ComputedResult<GHPullRequestPendingReview?>> =
    reviewDataProvider.createPendingReviewRequestsFlow().computationStateIn(cs)

  private val reviewCommentPreferred = settings.reviewCommentsPreferredState

  override val submitActions: StateFlow<List<SubmitAction>> =
    pendingReviewState.combineState(reviewCommentPreferred) { reviewResult, reviewPreferred ->
      val review = reviewResult.getOrNull()
      if (review == null) {
        if (reviewPreferred) listOf(createReviewAction, createSingleCommentAction)
        else listOf(createSingleCommentAction, createReviewAction)
      }
      else {
        listOf(createReviewCommentAction(review.id))
      }
    }

  override fun cancel() = onCancel()

  private val createSingleCommentAction = SubmitAction.CreateSingleComment {
    submit {
      val thread = createThreadDTO(it)
      val commitSha = position.change.revisionNumberAfter.asString()
      reviewDataProvider.createReview(EmptyProgressIndicator(), GHPullRequestReviewEvent.COMMENT, null, commitSha, listOf(thread)).await()
      settings.reviewCommentsPreferred = false
      cancel()
    }
  }

  private val createReviewAction = SubmitAction.CreateReview {
    submit {
      val thread = createThreadDTO(it)
      val commitSha = position.change.revisionNumberAfter.asString()
      reviewDataProvider.createReview(EmptyProgressIndicator(), null, null, commitSha, listOf(thread)).await()
      settings.reviewCommentsPreferred = true
      cancel()
    }
  }

  private fun createReviewCommentAction(reviewId: String) = SubmitAction.CreateReviewComment {
    submit {
      val filePath = relativePath(repository.root, position.change.filePath)
      val location = position.location
      val line = location.lineIdx.inc()
      if (position.isCumulative) {
        val startLine = location.asSafely<GHPRReviewCommentLocation.MultiLine>()?.startLineIdx?.inc() ?: line
        reviewDataProvider.createThread(EmptyProgressIndicator(), reviewId, it, line, location.side, startLine, filePath)
      }
      else {
        val commitSha = position.change.revisionNumberAfter.asString()
        reviewDataProvider.addComment(EmptyProgressIndicator(), reviewId, it, commitSha, filePath, location.side, line)
      }.await()
      cancel()
    }
  }

  private fun createThreadDTO(body: String): GHPullRequestDraftReviewThread {
    val filePath = relativePath(repository.root, position.change.filePath)
    return when (val location = position.location) {
      is GHPRReviewCommentLocation.MultiLine ->
        GHPullRequestDraftReviewThread(body, location.lineIdx.inc(), filePath, location.side, location.startLineIdx.inc(), location.side)
      is GHPRReviewCommentLocation.SingleLine ->
        GHPullRequestDraftReviewThread(body, location.lineIdx.inc(), filePath, location.side, null, null)
    }
  }
}