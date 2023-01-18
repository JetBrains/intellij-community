// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNote
import org.jetbrains.plugins.gitlab.util.SingleCoroutineLauncher

interface GitLabNoteEditingViewModel {
  val text: MutableStateFlow<String>

  val state: Flow<SubmissionState?>

  fun submit()

  suspend fun destroy()

  sealed interface SubmissionState {
    object Loading : SubmissionState
    class Error(val error: Throwable) : SubmissionState
  }
}

class GitLabNoteEditingViewModelImpl(parentCs: CoroutineScope,
                                     private val note: GitLabNote,
                                     private val onDone: suspend () -> Unit)
  : GitLabNoteEditingViewModel {

  private val cs = parentCs.childScope()
  private val taskLauncher = SingleCoroutineLauncher(cs)

  override val text: MutableStateFlow<String> = MutableStateFlow(note.body.value)

  private val _state = MutableStateFlow<GitLabNoteEditingViewModel.SubmissionState?>(null)
  override val state: Flow<GitLabNoteEditingViewModel.SubmissionState?> = _state.asSharedFlow()

  override fun submit() {
    taskLauncher.launch {
      val newText = text.first()
      _state.value = GitLabNoteEditingViewModel.SubmissionState.Loading
      try {
        note.updateBody(newText)
        _state.value = null
        onDone()
      }
      catch (ce: CancellationException) {
        throw ce
      }
      catch (e: Exception) {
        _state.value = GitLabNoteEditingViewModel.SubmissionState.Error(e)
      }
    }
  }

  override suspend fun destroy() {
    cs.coroutineContext[Job]!!.cancelAndJoin()
  }
}