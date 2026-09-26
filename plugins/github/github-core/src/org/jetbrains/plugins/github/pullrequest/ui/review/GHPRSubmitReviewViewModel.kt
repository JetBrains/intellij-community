// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.review

import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.ui.codereview.review.CodeReviewSubmitViewModel
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.workingTrees.GitWorkingTreesService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider

interface GHPRSubmitReviewViewModel : CodeReviewSubmitViewModel {
  /**
   * If the current user is the author of the pull request
   */
  val viewerIsAuthor: Boolean

  /**
   * If there's a currently pending review
   */
  val hasPendingReview: Boolean

  /**
   * Whether the review is being done in a dedicated Git worktree that can be removed after submitting
   * (controls visibility of the "delete worktree after submit" option).
   */
  val canDeleteWorktree: Boolean

  /**
   * Whether the review worktree should be removed after a successful submit. Meaningful only when [canDeleteWorktree].
   */
  val deleteWorktreeAfterSubmit: StateFlow<Boolean>

  /**
   * Update [deleteWorktreeAfterSubmit].
   */
  fun setDeleteWorktreeAfterSubmit(value: Boolean)

  /**
   * Discard the current pending review
   */
  fun discard()

  /**
   * Submit the review with a specified event
   */
  fun submit(event: GHPullRequestReviewEvent)
}

internal class GHPRSubmitReviewViewModelImpl(parentCs: CoroutineScope,
                                             private val project: Project,
                                             private val dataProvider: GHPRReviewDataProvider,
                                             override val viewerIsAuthor: Boolean,
                                             private val pendingReview: GHPullRequestPendingReview?,
                                             private val onDone: suspend () -> Unit)
  : GHPRSubmitReviewViewModel {
  private val cs = parentCs.childScope(this::class)

  private val taskLauncher = SingleCoroutineLauncher(cs)

  override val hasPendingReview: Boolean = pendingReview != null

  private val worktreeSessionState = project.service<GHPRReviewWorktreeSessionState>()

  override val canDeleteWorktree: Boolean = GitWorkingTreesService.getInstance(project).isCurrentProjectLinkedWorktree()

  // Defaults to unchecked on first use, but remembers the last choice for the rest of the IDE session.
  private val _deleteWorktreeAfterSubmit = MutableStateFlow(worktreeSessionState.deleteWorktreeAfterSubmit)
  override val deleteWorktreeAfterSubmit: StateFlow<Boolean> = _deleteWorktreeAfterSubmit.asStateFlow()

  override fun setDeleteWorktreeAfterSubmit(value: Boolean) {
    _deleteWorktreeAfterSubmit.value = value
    worktreeSessionState.deleteWorktreeAfterSubmit = value
  }

  override val isBusy: StateFlow<Boolean> = taskLauncher.busy
  private val _error = MutableStateFlow<Throwable?>(null)

  override val error: StateFlow<Throwable?> = _error.asStateFlow()

  override val draftCommentsCount: StateFlow<Int> = MutableStateFlow(pendingReview?.commentsCount ?: 0)

  override val text: MutableStateFlow<String> = dataProvider.pendingReviewComment

  override fun submit(event: GHPullRequestReviewEvent) {
    val body = text.value
    taskLauncher.launch {
      try {
        if (pendingReview != null) {
          dataProvider.submitReview(pendingReview.id, event, body)
        }
        else {
          dataProvider.createReview(event, body)
        }
      }
      catch (ce: CancellationException) {
        throw ce
      }
      catch (e: Exception) {
        _error.value = e
        return@launch
      }
      _error.value = null
      text.value = ""
      if (canDeleteWorktree && deleteWorktreeAfterSubmit.value) {
        // Runs on the worktree service scope, so it survives onDone() closing this project.
        GitWorkingTreesService.getInstance(project).deleteCurrentProjectWorktree()
      }
      onDone()
    }
  }

  override fun discard() {
    if (pendingReview != null) {
      taskLauncher.launch {
        dataProvider.deleteReview(pendingReview.id)
        onDone()
      }
    }
  }

  override fun cancel() {
    cs.launch {
      onDone()
    }
  }
}

@Service(Service.Level.PROJECT)
internal class GHPRReviewWorktreeSessionState {
  @Volatile
  var deleteWorktreeAfterSubmit: Boolean = false
}