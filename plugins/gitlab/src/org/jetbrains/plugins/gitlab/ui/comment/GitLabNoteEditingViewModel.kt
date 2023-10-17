// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.ui.util.bindEnabledIn
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.util.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import javax.swing.AbstractAction

interface GitLabNoteEditingViewModel {
  val canSubmitAsDraft: Boolean

  val text: MutableStateFlow<String>
  val focusRequests: Flow<Unit>

  val state: Flow<SubmissionState?>

  fun requestFocus()

  fun submit(asDraft: Boolean)

  suspend fun destroy()

  sealed interface SubmissionState {
    object Loading : SubmissionState
    class Error(val error: Throwable) : SubmissionState
    object Done : SubmissionState
  }
}

class DelegatingGitLabNoteEditingViewModel(parentCs: CoroutineScope,
                                           initialText: String,
                                           private val submitter: suspend (String) -> Unit,
                                           private val submitterAsDraft: (suspend (String) -> Unit)? = null)
  : GitLabNoteEditingViewModel {
  override val canSubmitAsDraft: Boolean = submitterAsDraft != null

  private val cs = parentCs.childScope()
  private val taskLauncher = SingleCoroutineLauncher(cs)

  override val text: MutableStateFlow<String> = MutableStateFlow(initialText)

  private val _focusRequestsChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
  override val focusRequests: Flow<Unit>
    get() = _focusRequestsChannel.receiveAsFlow()

  private val _state = MutableStateFlow<GitLabNoteEditingViewModel.SubmissionState?>(null)
  override val state: Flow<GitLabNoteEditingViewModel.SubmissionState?> = _state.asSharedFlow()

  override fun requestFocus() {
    cs.launch {
      _focusRequestsChannel.send(Unit)
    }
  }

  override fun submit(asDraft: Boolean) {
    taskLauncher.launch {
      val newText = text.first()
      _state.value = GitLabNoteEditingViewModel.SubmissionState.Loading
      try {
        if (asDraft) {
          submitterAsDraft?.invoke(newText) ?: error("Cannot be submitted as draft")
        }
        else {
          submitter(newText)
        }

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

interface NewGitLabNoteViewModel : GitLabNoteEditingViewModel {
  val currentUser: GitLabUserDTO
}

fun GitLabNoteEditingViewModel.forNewNote(currentUser: GitLabUserDTO): NewGitLabNoteViewModel =
  WrappingNewGitLabNoteViewModel(this, currentUser)

private class WrappingNewGitLabNoteViewModel(delegate: GitLabNoteEditingViewModel, override val currentUser: GitLabUserDTO)
  : NewGitLabNoteViewModel, GitLabNoteEditingViewModel by delegate

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
    submit(false)
  }.apply { bindEnabledIn(cs, enabledFlow) }
}

fun GitLabNoteEditingViewModel.submitAsDraftActionIn(cs: CoroutineScope, actionName: @Nls String): AbstractAction? {
  if (!canSubmitAsDraft) return null

  val enabledFlow = text.combine(state) { text, state -> text.isNotBlank() && state != GitLabNoteEditingViewModel.SubmissionState.Loading }
  return swingAction(actionName) {
    submit(true)
  }.apply { bindEnabledIn(cs, enabledFlow) }
}
