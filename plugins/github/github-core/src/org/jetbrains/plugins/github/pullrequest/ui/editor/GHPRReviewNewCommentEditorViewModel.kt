// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModel
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModelBase
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.filePath
import com.intellij.collaboration.util.getOrNull
import com.intellij.diff.util.LineRange
import com.intellij.openapi.project.Project
import com.intellij.util.asSafely
import com.intellij.vcsUtil.VcsFileUtil.relativePath
import git4idea.changes.GitBranchComparisonResult
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewThread
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.provider.changesComputationState
import org.jetbrains.plugins.github.pullrequest.data.provider.pendingReviewComputationFlow
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentPosition
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModel.SubmitAction
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider

interface GHPRReviewNewCommentEditorViewModel : CodeReviewSubmittableTextViewModel {
  val position: StateFlow<GHPRReviewCommentPosition>
  val currentUser: GHActor
  val avatarIconsProvider: GHAvatarIconsProvider
  val submitActions: StateFlow<List<SubmitAction>>

  fun cancel()
  fun updateLineRange(newStartLine: Int?, newEndLine: Int?)

  sealed interface SubmitAction : () -> Unit {
    fun interface CreateSingleComment : SubmitAction
    fun interface CreateReview : SubmitAction
    fun interface CreateReviewComment : SubmitAction
  }
}

internal class GHPRReviewNewCommentEditorViewModelImpl(
  override val project: Project,
  parentCs: CoroutineScope,
  dataProvider: GHPRDataProvider,
  private val repository: GitRepository,
  override val currentUser: GHActor,
  override val avatarIconsProvider: GHAvatarIconsProvider,
  pos: GHPRReviewCommentPosition,
  private val onCancel: (position: GHPRReviewCommentPosition) -> Unit,
) : CodeReviewSubmittableTextViewModelBase(project, parentCs, ""),
    GHPRReviewNewCommentEditorViewModel {
  private val settings = GithubPullRequestsProjectUISettings.getInstance(project)
  private val reviewDataProvider = dataProvider.reviewData
  private val changesState: StateFlow<ComputedResult<GitBranchComparisonResult>> =
    dataProvider.changesData.changesComputationState().stateInNow(cs, ComputedResult.loading())

  private val side = pos.location.side
  private val change = pos.change
  private val _position: MutableStateFlow<GHPRReviewCommentPosition> = MutableStateFlow(pos)
  override val position: StateFlow<GHPRReviewCommentPosition> = _position.asStateFlow()

  override fun updateLineRange(newStartLine: Int?, newEndLine: Int?) {
    val range = when (val location = position.value.location) {
      is GHPRReviewCommentLocation.SingleLine -> LineRange(location.lineIdx, location.lineIdx)
      is GHPRReviewCommentLocation.MultiLine -> LineRange(location.startLineIdx, location.lineIdx)
    }
    val newRange = LineRange(newStartLine ?: range.start, newEndLine ?: range.end)
    _position.value = if (newRange.start == newRange.end) {
      GHPRReviewCommentPosition(change, GHPRReviewCommentLocation.SingleLine(side, newRange.end))
    }
    else {
      GHPRReviewCommentPosition(change, GHPRReviewCommentLocation.MultiLine(side, newRange.start, newRange.end))
    }
    GHPRStatisticsCollector.logResizedComments(project)
  }

  private val pendingReviewState: StateFlow<ComputedResult<GHPullRequestPendingReview?>> =
    reviewDataProvider.pendingReviewComputationFlow.stateInNow(cs, ComputedResult.loading())

  private val reviewCommentPreferred = settings.reviewCommentsPreferredState

  private val createSingleCommentAction = SubmitAction.CreateSingleComment {
    submit {
      val thread = createThreadDTO(it)
      val commitSha = position.value.change.revisionNumberAfter.asString()
      reviewDataProvider.createReview(GHPullRequestReviewEvent.COMMENT, null, commitSha, listOf(thread))
      settings.reviewCommentsPreferred = false
      cancel()
    }
  }

  private val createReviewAction = SubmitAction.CreateReview {
    submit {
      val thread = createThreadDTO(it)
      val commitSha = position.value.change.revisionNumberAfter.asString()
      reviewDataProvider.createReview(null, null, commitSha, listOf(thread))
      settings.reviewCommentsPreferred = true
      cancel()
    }
  }

  override val submitActions: StateFlow<List<SubmitAction>> =
    combine(pendingReviewState, reviewCommentPreferred, changesState) { reviewResult, reviewPreferred, changesResult ->
      if (reviewResult.isSuccess && changesResult.isSuccess) {
        val review = reviewResult.getOrNull()
        val changes = changesResult.getOrNull()
        if (review == null) {
          if (reviewPreferred) listOf(createReviewAction, createSingleCommentAction)
          else listOf(createSingleCommentAction, createReviewAction)
        }
        else if (changes != null) {
          listOf(createReviewCommentAction(review.id, changes.changes.contains(position.value.change)))
        }
        else {
          emptyList()
        }
      }
      else {
        emptyList()
      }
    }.stateInNow(cs, emptyList())

  override fun cancel() = onCancel(position.value)

  private fun createReviewCommentAction(reviewId: String, isCumulative: Boolean) = SubmitAction.CreateReviewComment {
    submit {
      val filePath = relativePath(repository.root, position.value.change.filePath)
      val location = position.value.location
      val line = location.lineIdx.inc()
      if (isCumulative) {
        val startLine = location.asSafely<GHPRReviewCommentLocation.MultiLine>()?.startLineIdx?.inc() ?: line
        reviewDataProvider.createThread(reviewId, it, line, location.side, startLine, filePath)
        if (startLine < line) {
          GHPRStatisticsCollector.logMultilineCommentsCreated(project)
        }
      }
      else {
        val commitSha = position.value.change.revisionNumberAfter.asString()
        reviewDataProvider.addComment(reviewId, it, commitSha, filePath, location.side, line)
      }
      cancel()
    }
  }

  private fun createThreadDTO(body: String): GHPullRequestDraftReviewThread {
    val filePath = relativePath(repository.root, position.value.change.filePath)
    return when (val location = position.value.location) {
      is GHPRReviewCommentLocation.MultiLine ->
        GHPullRequestDraftReviewThread(body, location.lineIdx.inc(), filePath, location.side, location.startLineIdx.inc(), location.side)
      is GHPRReviewCommentLocation.SingleLine ->
        GHPullRequestDraftReviewThread(body, location.lineIdx.inc(), filePath, location.side, null, null)
    }
  }

  fun destroy() = cs.cancel()
}