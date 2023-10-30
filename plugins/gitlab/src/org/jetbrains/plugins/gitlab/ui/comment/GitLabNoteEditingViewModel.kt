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
import com.intellij.util.childScope
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
import javax.swing.AbstractAction

interface GitLabNoteEditingViewModel {
  val text: MutableStateFlow<String>
  val focusRequests: Flow<Unit>

  val state: Flow<SubmissionState?>

  fun requestFocus()

  fun submit()

  suspend fun destroy()

  sealed interface SubmissionState {
    data object Loading : SubmissionState
    class Error(val error: Throwable) : SubmissionState
    data object Done : SubmissionState
  }

  companion object {
    fun forExistingNote(
      parentCs: CoroutineScope,
      initialText: String,
      submitter: suspend (String) -> Unit
    ): GitLabNoteEditingViewModel =
      GitLabNoteEditingViewModelImpl(parentCs, initialText, submitter)

    fun forNewNote(
      parentCs: CoroutineScope,
      project: Project,
      currentUser: GitLabUserDTO,
      submitter: suspend (String) -> Unit,
      submitterAsDraft: (suspend (String) -> Unit)? = null
    ): NewGitLabNoteViewModel =
      NewGitLabNoteViewModelImpl(parentCs, "", project, currentUser, submitter, submitterAsDraft)
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

private class GitLabNoteEditingViewModelImpl(parentCs: CoroutineScope,
                                             initialText: String,
                                             private val submitter: suspend (String) -> Unit)
  : AbstractGitLabNoteEditingViewModel(parentCs, initialText) {
  override fun submit() {
    submit(submitter)
  }
}

interface NewGitLabNoteViewModel : GitLabNoteEditingViewModel {
  val canSubmitAsDraft: Boolean
  val usedAsDraftSubmitActionLast: StateFlow<Boolean>

  val currentUser: GitLabUserDTO

  fun submitAsDraft()
}

private class NewGitLabNoteViewModelImpl(
  parentCs: CoroutineScope,
  initialText: String,
  project: Project,
  override val currentUser: GitLabUserDTO,
  private val submitter: suspend (String) -> Unit,
  private val submitterAsDraft: (suspend (String) -> Unit)? = null
) : AbstractGitLabNoteEditingViewModel(parentCs, initialText), NewGitLabNoteViewModel {
  private val preferences = project.service<GitLabMergeRequestsPreferences>()

  override val canSubmitAsDraft: Boolean = submitterAsDraft != null
  override val usedAsDraftSubmitActionLast: StateFlow<Boolean> = channelFlow {
    val disposable = Disposer.newDisposable()
    preferences.addListener(disposable) {
      trySend(it.usedAsDraftSubmitActionLast)
    }
    awaitClose { Disposer.dispose(disposable) }
  }.stateIn(cs, SharingStarted.Lazily, preferences.usedAsDraftSubmitActionLast)

  override fun submit() {
    submit {
      submitter(it)
      preferences.usedAsDraftSubmitActionLast = false
    }
  }

  override fun submitAsDraft() {
    if (submitterAsDraft == null) error("Cannot be submitted as draft")
    submit {
      submitterAsDraft.invoke(it)
      preferences.usedAsDraftSubmitActionLast = true
    }
  }
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

fun GitLabNoteEditingViewModel.submitActionIn(cs: CoroutineScope, actionName: @Nls String): AbstractAction {
  val enabledFlow = text.combine(state) { text, state -> text.isNotBlank() && state != GitLabNoteEditingViewModel.SubmissionState.Loading }
  return swingAction(actionName) {
    submit()
  }.apply { bindEnabledIn(cs, enabledFlow) }
}

fun NewGitLabNoteViewModel.submitAsDraftActionIn(cs: CoroutineScope, actionName: @Nls String): AbstractAction? {
  if (!canSubmitAsDraft) return null

  val enabledFlow = text.combine(state) { text, state -> text.isNotBlank() && state != GitLabNoteEditingViewModel.SubmissionState.Loading }
  return swingAction(actionName) {
    submitAsDraft()
  }.apply { bindEnabledIn(cs, enabledFlow) }
}

fun NewGitLabNoteViewModel.primarySubmitActionIn(
  cs: CoroutineScope,
  submit: AbstractAction,
  submitAsDraft: AbstractAction?
): StateFlow<AbstractAction> =
  usedAsDraftSubmitActionLast.mapState(cs) {
    if (it && submitAsDraft != null) submitAsDraft
    else submit
  }

fun NewGitLabNoteViewModel.secondarySubmitActionIn(
  cs: CoroutineScope,
  submit: AbstractAction,
  submitAsDraft: AbstractAction?
): StateFlow<List<AbstractAction>> =
  usedAsDraftSubmitActionLast.mapState(cs) {
    if (it && submitAsDraft != null) listOf(submit)
    else listOfNotNull(submitAsDraft)
  }
