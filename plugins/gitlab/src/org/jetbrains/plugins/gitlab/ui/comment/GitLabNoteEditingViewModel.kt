// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.util.bindEnabledIn
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
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
import javax.swing.AbstractAction

interface GitLabNoteEditingViewModel {
  val text: MutableStateFlow<String>
  val focusRequests: Flow<Unit>

  val state: Flow<SubmissionState?>

  fun requestFocus()

  suspend fun destroy()

  sealed interface SubmissionState {
    data object Loading : SubmissionState
    class Error(val error: Throwable) : SubmissionState
    data object Done : SubmissionState
  }

  companion object {
    internal fun forExistingNote(
      parentCs: CoroutineScope,
      note: MutableGitLabNote
    ): ExistingGitLabNoteEditingViewModel =
      GitLabNoteEditingViewModelImpl(parentCs, note)

    internal fun forNewNote(
      parentCs: CoroutineScope,
      project: Project,
      mergeRequest: GitLabMergeRequest,
      currentUser: GitLabUserDTO
    ): NewGitLabNoteViewModel =
      NewStandaloneGitLabNoteViewModel(parentCs, "", project, mergeRequest, currentUser)

    internal fun forNewDiffNote(
      parentCs: CoroutineScope,
      project: Project,
      mergeRequest: GitLabMergeRequest,
      currentUser: GitLabUserDTO,
      position: GitLabMergeRequestNewDiscussionPosition
    ): NewGitLabNoteViewModel =
      NewDiffGitLabNoteViewModel(parentCs, "", project, mergeRequest, currentUser, position)

    internal fun forReplyNote(
      parentCs: CoroutineScope,
      project: Project,
      discussion: GitLabDiscussion,
      currentUser: GitLabUserDTO
    ): NewGitLabNoteViewModel =
      NewReplyGitLabNoteViewModel(parentCs, "", project, discussion, currentUser)
  }
}

abstract class AbstractGitLabNoteEditingViewModel(parentCs: CoroutineScope, initialText: String) : GitLabNoteEditingViewModel {
  protected val cs = parentCs.childScope()
  private val taskLauncher = SingleCoroutineLauncher(cs)

  override val text: MutableStateFlow<String> = MutableStateFlow(initialText)

  private val _state = MutableStateFlow<GitLabNoteEditingViewModel.SubmissionState?>(null)
  override val state: Flow<GitLabNoteEditingViewModel.SubmissionState?> = _state.asSharedFlow()

  private val _focusRequestsChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
  override val focusRequests: Flow<Unit>
    get() = _focusRequestsChannel.receiveAsFlow()

  override fun requestFocus() {
    cs.launch {
      _focusRequestsChannel.send(Unit)
    }
  }

  protected fun submit(submitter: suspend (String) -> Unit) {
    taskLauncher.launch {
      val newText = text.first()
      _state.value = GitLabNoteEditingViewModel.SubmissionState.Loading
      try {
        submitter(newText)

        _state.value = GitLabNoteEditingViewModel.SubmissionState.Done
      }
      catch (ce: CancellationException) {
        _state.value = GitLabNoteEditingViewModel.SubmissionState.Done
        throw ce
      }
      catch (e: Exception) {
        _state.value = GitLabNoteEditingViewModel.SubmissionState.Error(e)
      }
    }
  }

  override suspend fun destroy() = cs.cancelAndJoinSilently()
}

interface ExistingGitLabNoteEditingViewModel : GitLabNoteEditingViewModel {
  fun save()
}

private class GitLabNoteEditingViewModelImpl(parentCs: CoroutineScope, private val note: MutableGitLabNote)
  : AbstractGitLabNoteEditingViewModel(parentCs, note.body.value), ExistingGitLabNoteEditingViewModel {
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
  parentCs: CoroutineScope,
  initialText: String,
  project: Project,
  override val currentUser: GitLabUserDTO
) : AbstractGitLabNoteEditingViewModel(parentCs, initialText), NewGitLabNoteViewModel {
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

private class NewStandaloneGitLabNoteViewModel(parentCs: CoroutineScope,
                                               initialText: String,
                                               project: Project,
                                               private val mergeRequest: GitLabMergeRequest,
                                               currentUser: GitLabUserDTO)
  : NewGitLabNoteViewModelBase(parentCs, initialText, project, currentUser) {
  override val canSubmitAsDraft: Boolean = mergeRequest.canAddDraftNotes
  override suspend fun doSubmit(text: String) = mergeRequest.addNote(text)
  override suspend fun doSubmitAsDraft(text: String) = mergeRequest.addDraftNote(text)
}

private class NewDiffGitLabNoteViewModel(parentCs: CoroutineScope,
                                         initialText: String,
                                         project: Project,
                                         private val mergeRequest: GitLabMergeRequest,
                                         currentUser: GitLabUserDTO,
                                         private val position: GitLabMergeRequestNewDiscussionPosition)
  : NewGitLabNoteViewModelBase(parentCs, initialText, project, currentUser) {
  override val canSubmitAsDraft: Boolean = mergeRequest.canAddPositionalDraftNotes
  override suspend fun doSubmit(text: String) = mergeRequest.addNote(position, text)
  override suspend fun doSubmitAsDraft(text: String) = mergeRequest.addDraftNote(position, text)
}

private class NewReplyGitLabNoteViewModel(parentCs: CoroutineScope,
                                          initialText: String,
                                          project: Project,
                                          private val discussion: GitLabDiscussion,
                                          currentUser: GitLabUserDTO)
  : NewGitLabNoteViewModelBase(parentCs, initialText, project, currentUser) {
  override val canSubmitAsDraft: Boolean = discussion.canAddDraftNotes
  override suspend fun doSubmit(text: String) = discussion.addNote(text)
  override suspend fun doSubmitAsDraft(text: String) = discussion.addDraftNote(text)
}

fun GitLabNoteEditingViewModel.onDoneIn(cs: CoroutineScope, callback: suspend () -> Unit) {
  cs.launch {
    state.filter { state ->
      state == GitLabNoteEditingViewModel.SubmissionState.Done
    }.collect {
      callback()
    }
  }
}

internal fun ExistingGitLabNoteEditingViewModel.saveActionIn(cs: CoroutineScope, actionName: @Nls String,
                                                             project: Project, place: GitLabStatistics.MergeRequestNoteActionPlace)
  : AbstractAction {
  val enabledFlow = text.combine(state) { text, state -> text.isNotBlank() && state != GitLabNoteEditingViewModel.SubmissionState.Loading }
  return swingAction(actionName) {
    save()
    GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.UPDATE_NOTE, place)
  }.apply { bindEnabledIn(cs, enabledFlow) }
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
                                                   place: GitLabStatistics.MergeRequestNoteActionPlace): AbstractAction {
  val enabledFlow = text.combine(state) { text, state -> text.isNotBlank() && state != GitLabNoteEditingViewModel.SubmissionState.Loading }
  return swingAction(actionName) {
    submit()
    GitLabStatistics.logMrActionExecuted(project, type.toStatAction(false), place)
  }.apply { bindEnabledIn(cs, enabledFlow) }
}

internal fun NewGitLabNoteViewModel.submitAsDraftActionIn(cs: CoroutineScope, actionName: @Nls String,
                                                          project: Project, type: NewGitLabNoteType,
                                                          place: GitLabStatistics.MergeRequestNoteActionPlace): AbstractAction? {
  if (!canSubmitAsDraft) return null

  val enabledFlow = text.combine(state) { text, state -> text.isNotBlank() && state != GitLabNoteEditingViewModel.SubmissionState.Loading }
  return swingAction(actionName) {
    submitAsDraft()
    GitLabStatistics.logMrActionExecuted(project, type.toStatAction(true), place)
  }.apply { bindEnabledIn(cs, enabledFlow) }
}

internal fun NewGitLabNoteViewModel.primarySubmitActionIn(
  cs: CoroutineScope,
  submit: AbstractAction,
  submitAsDraft: AbstractAction?
): StateFlow<AbstractAction> =
  usedAsDraftSubmitActionLast.mapState(cs) {
    if (it && submitAsDraft != null) submitAsDraft
    else submit
  }

internal fun NewGitLabNoteViewModel.secondarySubmitActionIn(
  cs: CoroutineScope,
  submit: AbstractAction,
  submitAsDraft: AbstractAction?
): StateFlow<List<AbstractAction>> =
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
