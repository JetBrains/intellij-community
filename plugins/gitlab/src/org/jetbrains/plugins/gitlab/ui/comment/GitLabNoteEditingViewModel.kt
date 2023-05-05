// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.util.SingleCoroutineLauncher

interface GitLabNoteEditingViewModel {
  val text: MutableStateFlow<String>
  val focusRequests: Flow<Unit>

  val state: Flow<SubmissionState?>

  fun requestFocus()

  fun submit()

  suspend fun destroy()

  sealed interface SubmissionState {
    object Loading : SubmissionState
    class Error(val error: Throwable) : SubmissionState
    object Done : SubmissionState
  }
}

class DelegatingGitLabNoteEditingViewModel(parentCs: CoroutineScope,
                                           initialText: String,
                                           private val submitter: suspend (String) -> Unit)
  : GitLabNoteEditingViewModel {

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

  override fun submit() {
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

  override suspend fun destroy() {
    try {
      cs.coroutineContext[Job]!!.cancelAndJoin()
    }
    catch (e: CancellationException) {
      // ignore, cuz we don't want to cancel the invoker
    }
  }
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