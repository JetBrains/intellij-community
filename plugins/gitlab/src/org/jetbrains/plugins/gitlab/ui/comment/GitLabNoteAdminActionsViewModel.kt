// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.mergerequest.data.MutableGitLabNote

interface GitLabNoteAdminActionsViewModel {
  val busy: Flow<Boolean>

  val editVm: Flow<ExistingGitLabNoteEditingViewModel?>

  /**
   * Whether the note can be edited.
   */
  fun canEdit(): Boolean

  /**
   * Whether the note can be individually submitted when it is a draft note.
   */
  fun canSubmit(): Boolean

  fun startEditing()
  fun stopEditing()

  fun delete()

  /**
   * Submits the draft note so that it is visible for other users.
   */
  fun submitDraft()
}

private val LOG = logger<GitLabNoteAdminActionsViewModel>()

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabNoteAdminActionsViewModelImpl(
  parentCs: CoroutineScope,
  private val project: Project,
  private val note: MutableGitLabNote
) : GitLabNoteAdminActionsViewModel {

  private val cs = parentCs.childScope()
  private val taskLauncher = SingleCoroutineLauncher(cs)
  override val busy: Flow<Boolean> = taskLauncher.busy

  private val isEditing = MutableStateFlow(false)
  override val editVm: Flow<ExistingGitLabNoteEditingViewModel?> = isEditing.transformLatest { editing ->
    if (editing) {
      coroutineScope {
        val cs = this@coroutineScope
        val editVm = GitLabNoteEditingViewModel.forExistingNote(cs, project, note).apply {
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

  override fun canEdit(): Boolean =
    note.canEdit()

  override fun canSubmit(): Boolean =
    note.canSubmit()

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

  override fun submitDraft() {
    taskLauncher.launch {
      try {
        note.submit()
      }
      catch (e: Exception) {
        if (e is CancellationException) throw e
        LOG.warn(e)
        //TODO: handle???
      }
    }
  }
}
