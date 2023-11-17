// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModel
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModelBase
import com.intellij.collaboration.ui.codereview.comment.submitActionIn
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabDiscussion
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestNewDiscussionPosition
import org.jetbrains.plugins.gitlab.mergerequest.data.MutableGitLabNote
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import javax.swing.Action

interface GitLabNoteEditingViewModel : CodeReviewSubmittableTextViewModel {
  suspend fun destroy()

  companion object {
    internal fun forExistingNote(
      parentCs: CoroutineScope,
      project: Project,
      note: MutableGitLabNote
    ): ExistingGitLabNoteEditingViewModel =
      GitLabNoteEditingViewModelImpl(project, parentCs, note)

    internal fun forNewNote(
      parentCs: CoroutineScope,
      project: Project,
      mergeRequest: GitLabMergeRequest,
      currentUser: GitLabUserDTO
    ): NewGitLabNoteViewModel =
      NewStandaloneGitLabNoteViewModel(project, parentCs, "", mergeRequest, currentUser)

    internal fun forNewDiffNote(
      parentCs: CoroutineScope,
      project: Project,
      mergeRequest: GitLabMergeRequest,
      currentUser: GitLabUserDTO,
      position: GitLabMergeRequestNewDiscussionPosition
    ): NewGitLabNoteViewModel =
      NewDiffGitLabNoteViewModel(project, parentCs, "", mergeRequest, currentUser, position)

    internal fun forReplyNote(
      parentCs: CoroutineScope,
      project: Project,
      discussion: GitLabDiscussion,
      currentUser: GitLabUserDTO
    ): NewGitLabNoteViewModel =
      NewReplyGitLabNoteViewModel(project, parentCs, "", discussion, currentUser)
  }
}

abstract class AbstractGitLabNoteEditingViewModel(
  override val project: Project,
  parentCs: CoroutineScope,
  initialText: String
) : CodeReviewSubmittableTextViewModelBase(project, parentCs, initialText), GitLabNoteEditingViewModel {
  override suspend fun destroy() = cs.cancelAndJoinSilently()
}

interface ExistingGitLabNoteEditingViewModel : GitLabNoteEditingViewModel {
  fun save()
}

private class GitLabNoteEditingViewModelImpl(project: Project, parentCs: CoroutineScope, private val note: MutableGitLabNote)
  : AbstractGitLabNoteEditingViewModel(project, parentCs, note.body.value), ExistingGitLabNoteEditingViewModel {
  override fun save() {
    submit(note::setBody)
  }
}

interface NewGitLabNoteViewModel : GitLabNoteEditingViewModel {
  val canSubmitAsDraft: Boolean
  val usedAsDraftSubmitActionLast: StateFlow<Boolean>

  val currentUser: GitLabUserDTO

  fun submit()
  fun submitAsDraft()
}

private abstract class NewGitLabNoteViewModelBase(
  project: Project,
  parentCs: CoroutineScope,
  initialText: String,
  override val currentUser: GitLabUserDTO
) : AbstractGitLabNoteEditingViewModel(project, parentCs, initialText), NewGitLabNoteViewModel {
  private val preferences = project.service<GitLabMergeRequestsPreferences>()

  override val usedAsDraftSubmitActionLast: StateFlow<Boolean> = channelFlow {
    val disposable = Disposer.newDisposable()
    preferences.addListener(disposable) {
      trySend(it.usedAsDraftSubmitActionLast)
    }
    awaitClose { Disposer.dispose(disposable) }
  }.stateIn(cs, SharingStarted.Lazily, preferences.usedAsDraftSubmitActionLast)

  override fun submit() {
    submit {
      doSubmit(it)
      preferences.usedAsDraftSubmitActionLast = false
    }
  }

  protected abstract suspend fun doSubmit(text: String)

  override fun submitAsDraft() {
    require(canSubmitAsDraft) { "Cannot be submitted as draft" }
    submit {
      doSubmitAsDraft(it)
      preferences.usedAsDraftSubmitActionLast = true
    }
  }

  protected abstract suspend fun doSubmitAsDraft(text: String)
}

private class NewStandaloneGitLabNoteViewModel(project: Project,
                                               parentCs: CoroutineScope,
                                               initialText: String,
                                               private val mergeRequest: GitLabMergeRequest,
                                               currentUser: GitLabUserDTO)
  : NewGitLabNoteViewModelBase(project, parentCs, initialText, currentUser) {
  override val canSubmitAsDraft: Boolean = mergeRequest.canAddDraftNotes
  override suspend fun doSubmit(text: String) = mergeRequest.addNote(text)
  override suspend fun doSubmitAsDraft(text: String) = mergeRequest.addDraftNote(text)
}

private class NewDiffGitLabNoteViewModel(project: Project,
                                         parentCs: CoroutineScope,
                                         initialText: String,
                                         private val mergeRequest: GitLabMergeRequest,
                                         currentUser: GitLabUserDTO,
                                         private val position: GitLabMergeRequestNewDiscussionPosition)
  : NewGitLabNoteViewModelBase(project, parentCs, initialText, currentUser) {
  override val canSubmitAsDraft: Boolean = mergeRequest.canAddPositionalDraftNotes
  override suspend fun doSubmit(text: String) = mergeRequest.addNote(position, text)
  override suspend fun doSubmitAsDraft(text: String) = mergeRequest.addDraftNote(position, text)
}

private class NewReplyGitLabNoteViewModel(project: Project,
                                          parentCs: CoroutineScope,
                                          initialText: String,
                                          private val discussion: GitLabDiscussion,
                                          currentUser: GitLabUserDTO)
  : NewGitLabNoteViewModelBase(project, parentCs, initialText, currentUser) {
  override val canSubmitAsDraft: Boolean = discussion.canAddDraftNotes
  override suspend fun doSubmit(text: String) = discussion.addNote(text)
  override suspend fun doSubmitAsDraft(text: String) = discussion.addDraftNote(text)
}

fun GitLabNoteEditingViewModel.onDoneIn(cs: CoroutineScope, callback: suspend () -> Unit) {
  cs.launch {
    state.filter { state ->
      state?.isSuccess == true
    }.collect {
      callback()
    }
  }
}

internal fun ExistingGitLabNoteEditingViewModel.saveActionIn(cs: CoroutineScope, actionName: @Nls String,
                                                             project: Project, place: GitLabStatistics.MergeRequestNoteActionPlace)
  : Action = submitActionIn(cs, actionName) {
    save()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.UPDATE_NOTE, place)
}

internal enum class NewGitLabNoteType {
  STANDALONE, DIFF, REPLY
}

private fun NewGitLabNoteType.toStatAction(isDraft: Boolean): GitLabStatistics.MergeRequestAction = when (this) {
  NewGitLabNoteType.STANDALONE -> if (isDraft) GitLabStatistics.MergeRequestAction.ADD_NOTE else GitLabStatistics.MergeRequestAction.ADD_DRAFT_NOTE
  NewGitLabNoteType.DIFF -> if (isDraft) GitLabStatistics.MergeRequestAction.ADD_DIFF_NOTE else GitLabStatistics.MergeRequestAction.ADD_DRAFT_DIFF_NOTE
  NewGitLabNoteType.REPLY -> if (isDraft) GitLabStatistics.MergeRequestAction.ADD_DISCUSSION_NOTE else GitLabStatistics.MergeRequestAction.ADD_DRAFT_DISCUSSION_NOTE
}

internal fun NewGitLabNoteViewModel.submitActionIn(cs: CoroutineScope, actionName: @Nls String,
                                                   project: Project, type: NewGitLabNoteType,
                                                   place: GitLabStatistics.MergeRequestNoteActionPlace): Action =
  submitActionIn(cs, actionName) {
    submit()
    GitLabStatistics.logMrActionExecuted(project, type.toStatAction(false), place)
  }

internal fun NewGitLabNoteViewModel.submitAsDraftActionIn(cs: CoroutineScope, actionName: @Nls String,
                                                          project: Project, type: NewGitLabNoteType,
                                                          place: GitLabStatistics.MergeRequestNoteActionPlace): Action? {
  if (!canSubmitAsDraft) return null
  return submitActionIn(cs, actionName) {
    submitAsDraft()
    GitLabStatistics.logMrActionExecuted(project, type.toStatAction(true), place)
  }
}

internal fun NewGitLabNoteViewModel.primarySubmitActionIn(
  cs: CoroutineScope,
  submit: Action,
  submitAsDraft: Action?
): StateFlow<Action> =
  usedAsDraftSubmitActionLast.mapState(cs) {
    if (it && submitAsDraft != null) submitAsDraft
    else submit
  }

internal fun NewGitLabNoteViewModel.secondarySubmitActionIn(
  cs: CoroutineScope,
  submit: Action,
  submitAsDraft: Action?
): StateFlow<List<Action>> =
  usedAsDraftSubmitActionLast.mapState(cs) {
    if (it && submitAsDraft != null) listOf(submit)
    else listOfNotNull(submitAsDraft)
  }

internal fun NewGitLabNoteViewModel.submitActionHintIn(
  cs: CoroutineScope,
  mainHint: @Nls String,
  draftHint: @Nls String
): StateFlow<@Nls String> =
  if (!canSubmitAsDraft) MutableStateFlow(mainHint)
  else usedAsDraftSubmitActionLast.mapState(cs) { if (it) draftHint else mainHint }
