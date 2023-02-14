// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNote
import org.jetbrains.plugins.gitlab.util.SingleCoroutineLauncher

interface GitLabNoteAdminActionsViewModel {
  val busy: Flow<Boolean>

  val editVm: Flow<GitLabNoteEditingViewModel?>

  fun startEditing()
  fun stopEditing()

  fun delete()
}

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabNoteAdminActionsViewModelImpl(parentCs: CoroutineScope, private val note: GitLabNote)
  : GitLabNoteAdminActionsViewModel {

  private val cs = parentCs.childScope()
  private val taskLauncher = SingleCoroutineLauncher(cs)
  override val busy: Flow<Boolean> = taskLauncher.busy

  private val isEditing = MutableStateFlow(false)
  override val editVm: Flow<GitLabNoteEditingViewModel?> = isEditing.transformLatest { editing ->
    if (editing) {
      coroutineScope {
        val cs = this@coroutineScope
        val editVm = DelegatingGitLabNoteEditingViewModel(cs, note.body.value, note::setBody).apply {
          onDoneIn(cs) {
            stopEditing()
          }
        }
        emit(editVm)
        awaitCancellation()
      }
    }
    else {
      emit(null)
    }
  }.shareIn(cs, SharingStarted.Lazily, 1)

  override fun delete() {
    taskLauncher.launch {
      try {
        note.delete()
      }
      catch (e: Exception) {
        if (e is CancellationException) throw e
        //TODO: handle???
      }
    }
  }

  override fun startEditing() {
    isEditing.value = true
  }

  override fun stopEditing() {
    isEditing.value = false
  }
}
