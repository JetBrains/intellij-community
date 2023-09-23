// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.mergerequest.data.MutableGitLabNote
import com.intellij.collaboration.util.SingleCoroutineLauncher

interface GitLabNoteAdminActionsViewModel {
  val busy: Flow<Boolean>

  val editVm: Flow<GitLabNoteEditingViewModel?>

  /**
   * Whether the note can be edited.
   */
  suspend fun canEdit(): Boolean

  fun startEditing()
  fun stopEditing()

  fun delete()
}

private val LOG = logger<GitLabNoteAdminActionsViewModel>()

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabNoteAdminActionsViewModelImpl(parentCs: CoroutineScope, private val note: MutableGitLabNote)
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

  override suspend fun canEdit(): Boolean =
    note.canEdit()

  override fun delete() {
    taskLauncher.launch {
      try {
        note.delete()
      }
      catch (e: Exception) {
        if (e is CancellationException) throw e
        LOG.warn(e)
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
