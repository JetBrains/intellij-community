// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.util.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDiscussionsModel
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

open class GitLabNoteEditingViewModelBase(parentCs: CoroutineScope,
                                          initialText: String,
                                          private val submitter: suspend (String) -> Unit)
  : GitLabNoteEditingViewModel {

  private val cs = parentCs.childScope()
  private val taskLauncher = SingleCoroutineLauncher(cs)

  override val text: MutableStateFlow<String> = MutableStateFlow(initialText)

  private val _state = MutableStateFlow<GitLabNoteEditingViewModel.SubmissionState?>(null)
  override val state: Flow<GitLabNoteEditingViewModel.SubmissionState?> = _state.asSharedFlow()

  override fun submit() {
    taskLauncher.launch {
      val newText = text.first()
      _state.value = GitLabNoteEditingViewModel.SubmissionState.Loading
      try {
        submitter(newText)
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

  protected open suspend fun onDone() = Unit

  override suspend fun destroy() {
    try {
      cs.coroutineContext[Job]!!.cancelAndJoin()
    }
    catch (e: CancellationException) {
      // ignore, cuz we don't want to cancel the invoker
    }
  }
}


class EditGitLabNoteViewModel(parentCs: CoroutineScope, note: GitLabNote, private val onDone: (suspend () -> Unit))
  : GitLabNoteEditingViewModelBase(parentCs, note.body.value, note::setBody) {
  override suspend fun onDone() = onDone.invoke()
}


interface NewGitLabNoteViewModel : GitLabNoteEditingViewModel {
  val currentUser: GitLabUserDTO
}

class NewGitLabNoteViewModelImpl(parentCs: CoroutineScope,
                                 override val currentUser: GitLabUserDTO,
                                 discussionsModel: GitLabMergeRequestDiscussionsModel)
  : NewGitLabNoteViewModel, GitLabNoteEditingViewModelBase(parentCs, "", discussionsModel::addNote) {
  override suspend fun onDone() {
    text.value = ""
  }
}