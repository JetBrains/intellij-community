// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.review

import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestSubmitReviewViewModel.SubmittableReview

interface GitLabMergeRequestSubmitReviewViewModel {
  val isBusy: Flow<Boolean>
  val error: Flow<Throwable?>

  val draftCommentsCount: Flow<Int>
  val isApproved: Flow<Boolean>

  val text: MutableStateFlow<String>

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
   * Cancel the submission
   */
  fun cancel()

  data class SubmittableReview(val draftComments: Int)
}

internal fun GitLabMergeRequest.getSubmittableReview(currentUser: GitLabUserDTO): Flow<SubmittableReview?> =
  combine(draftNotes, details) { draftNotesResult, details ->
    val permissions = details.userPermissions
    val approvals = details.approvedBy
    val draftComments = draftNotesResult.getOrNull()?.count() ?: return@combine null
    val canChangeApproval = (permissions.canApprove ?: false) || approvals.any { it.id == currentUser.id }
    if (draftComments > 0 || canChangeApproval) SubmittableReview(draftComments)
    else null
  }

internal class GitLabMergeRequestSubmitReviewViewModelImpl(
  parentCs: CoroutineScope,
  private val mergeRequest: GitLabMergeRequest,
  private val currentUser: GitLabUserDTO,
  private val onDone: () -> Unit
) : GitLabMergeRequestSubmitReviewViewModel {
  private val cs = parentCs.childScope(Dispatchers.Default)
  private val taskLauncher = SingleCoroutineLauncher(cs)

  override val isBusy: Flow<Boolean> = taskLauncher.busy
  private val _error = MutableStateFlow<Throwable?>(null)
  override val error: Flow<Throwable?> get() = _error.asStateFlow()

  override val draftCommentsCount: Flow<Int> = mergeRequest.draftNotes.map { result -> result.getOrNull()?.count() ?: 0 }
  override val isApproved: Flow<Boolean> = mergeRequest.details.map { it.approvedBy.any { user -> user.id == currentUser.id } }
    .modelFlow(cs, thisLogger())
  override val text: MutableStateFlow<String> = mergeRequest.draftReviewText


  override fun approve() {
    taskLauncher.launch {
      executeAndSaveError {
        mergeRequest.submitDraftNotes()
        addNoteIfNotEmpty()
        mergeRequest.approve()
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
        onDone()
        text.value = ""
      }
    }
  }

  override fun cancel() {
    onDone()
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