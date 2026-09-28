// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.review

import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.ui.codereview.review.CodeReviewSubmitViewModel
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import git4idea.workingTrees.GitWorkingTreesService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestSubmitReviewViewModel.SubmittableReview

interface GitLabMergeRequestSubmitReviewViewModel : CodeReviewSubmitViewModel {
  /**
   * State of approval by a current user
   */
  val isApproved: StateFlow<Boolean>

  /**
   * Submit all draft comments, a new comment and approve MR
   */
  fun approve()

  /**
   * Submit all draft comments, a new comment and unapprove MR
   */
  fun unApprove()

  /**
   * Submit all draft comments and a new comment
   */
  fun submit()

  /**
   * Whether the review happens in a dedicated Git worktree that can be removed after a submit.
   * Controls the visibility of the "delete the worktree" option.
   */
  val canDeleteWorktree: Boolean

  /**
   * Whether to remove the review worktree after a successful submit. Only meaningful when [canDeleteWorktree].
   */
  val deleteWorktreeAfterSubmit: StateFlow<Boolean>

  /**
   * Update [deleteWorktreeAfterSubmit].
   */
  fun setDeleteWorktreeAfterSubmit(value: Boolean)

  data class SubmittableReview(val draftComments: Int, val isApprovedByViewer: Boolean)
}

internal fun GitLabMergeRequest.getSubmittableReview(currentUser: GitLabUserDTO): Flow<SubmittableReview?> =
  combine(draftNotes, details) { draftNotesResult, details ->
    val permissions = details.userPermissions
    val approvals = details.approvedBy
    val draftComments = draftNotesResult.getOrNull()?.count() ?: return@combine null
    val approvedByCurrentUser = approvals.any { it.id == currentUser.id }
    val canChangeApproval = (permissions.canApprove ?: true) || approvedByCurrentUser
    if (draftComments > 0 || canChangeApproval) SubmittableReview(draftComments, approvedByCurrentUser)
    else null
  }

internal class GitLabMergeRequestSubmitReviewViewModelImpl(
  parentCs: CoroutineScope,
  private val project: Project,
  private val mergeRequest: GitLabMergeRequest,
  private val currentUser: GitLabUserDTO,
  currentReview: SubmittableReview,
  private val onDone: () -> Unit,
) : GitLabMergeRequestSubmitReviewViewModel {
  private val cs = parentCs.childScope(this::class, Dispatchers.Default)
  private val taskLauncher = SingleCoroutineLauncher(cs)

  private val worktreeSessionState = project.service<GitLabReviewWorktreeSessionState>()

  override val canDeleteWorktree: Boolean = GitWorkingTreesService.getInstance(project).isCurrentProjectLinkedWorktree()

  // Unchecked on the first use. The last choice is kept for the rest of the IDE session.
  private val _deleteWorktreeAfterSubmit = MutableStateFlow(worktreeSessionState.deleteWorktreeAfterSubmit)
  override val deleteWorktreeAfterSubmit: StateFlow<Boolean> = _deleteWorktreeAfterSubmit.asStateFlow()

  override fun setDeleteWorktreeAfterSubmit(value: Boolean) {
    _deleteWorktreeAfterSubmit.value = value
    worktreeSessionState.deleteWorktreeAfterSubmit = value
  }

  override val isBusy: StateFlow<Boolean> = taskLauncher.busy
  private val _error = MutableStateFlow<Throwable?>(null)
  override val error: StateFlow<Throwable?> = _error.asStateFlow()

  override val draftCommentsCount: StateFlow<Int> = mergeRequest.draftNotes.map { result -> result.getOrNull()?.count() ?: 0 }
    .stateIn(cs, SharingStarted.Eagerly, currentReview.draftComments)
  override val isApproved: StateFlow<Boolean> = mergeRequest.details.map {
    it.approvedBy.any { user -> user.id == currentUser.id }
  }.stateIn(cs, SharingStarted.Eagerly, currentReview.isApprovedByViewer)
  override val text: MutableStateFlow<String> = mergeRequest.draftReviewText


  override fun approve() {
    taskLauncher.launch {
      executeAndSaveError {
        mergeRequest.submitDraftNotes()
        addNoteIfNotEmpty()
        mergeRequest.approve()
        deleteWorktreeIfRequested()
        onDone()
        text.value = ""
      }
    }
  }

  override fun unApprove() {
    taskLauncher.launch {
      executeAndSaveError {
        mergeRequest.submitDraftNotes()
        addNoteIfNotEmpty()
        mergeRequest.unApprove()
        deleteWorktreeIfRequested()
        onDone()
        text.value = ""
      }
    }
  }

  override fun submit() {
    taskLauncher.launch {
      executeAndSaveError {
        mergeRequest.submitDraftNotes()
        addNoteIfNotEmpty()
        mergeRequest.refreshData()
        deleteWorktreeIfRequested()
        onDone()
        text.value = ""
      }
    }
  }

  override fun cancel() {
    onDone()
  }

  private fun deleteWorktreeIfRequested() {
    if (canDeleteWorktree && deleteWorktreeAfterSubmit.value) {
      // Runs on the worktree service scope, so it survives when onDone() closes this project.
      GitWorkingTreesService.getInstance(project).deleteCurrentProjectWorktree()
    }
  }

  private suspend fun addNoteIfNotEmpty() {
    text.value.takeIf { it.isNotEmpty() }?.let { mergeRequest.addNote(it) }
  }

  private suspend inline fun executeAndSaveError(crossinline task: suspend () -> Unit) {
    try {
      task()
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      _error.value = e
    }
  }
}

@Service(Service.Level.PROJECT)
internal class GitLabReviewWorktreeSessionState {
  @Volatile
  var deleteWorktreeAfterSubmit: Boolean = false
}