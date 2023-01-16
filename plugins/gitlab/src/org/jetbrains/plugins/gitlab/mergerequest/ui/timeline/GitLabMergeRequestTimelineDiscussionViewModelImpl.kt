// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.mapScoped
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabDiscussionDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.comment.GitLabMergeRequestNoteViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.comment.LoadedGitLabMergeRequestNoteViewModel

interface GitLabMergeRequestTimelineDiscussionViewModel {
  val author: Flow<GitLabUserDTO>

  val mainNote: Flow<GitLabMergeRequestNoteViewModel>
  val replies: Flow<List<GitLabMergeRequestNoteViewModel>>

  val repliesFolded: Flow<Boolean>

  fun setRepliesFolded(folded: Boolean)
}

class GitLabMergeRequestTimelineDiscussionViewModelImpl(
  parentCs: CoroutineScope,
  discussion: GitLabDiscussionDTO
) : GitLabMergeRequestTimelineDiscussionViewModel {

  init {
    require(discussion.notes.isNotEmpty()) { "Discussion with empty notes" }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private val cs = parentCs.childScope(Dispatchers.Default.limitedParallelism(1))

  private val notesState = MutableStateFlow(discussion.notes)

  override val mainNote: Flow<GitLabMergeRequestNoteViewModel> = notesState.mapScoped {
    createNoteVm(this, it.first())
  }.share()

  private val _repliesFolded = MutableStateFlow(true)
  override val repliesFolded: Flow<Boolean> = _repliesFolded.asStateFlow()

  override val replies: Flow<List<GitLabMergeRequestNoteViewModel>> =
    notesState.mapScoped { notesList ->
      notesList.asSequence().drop(1).map { createNoteVm(this, it) }.toList()
    }.share()

  override val author: Flow<GitLabUserDTO> = mainNote.map { it.author }

  override fun setRepliesFolded(folded: Boolean) {
    _repliesFolded.value = folded
  }

  private fun createNoteVm(parentCs: CoroutineScope, note: GitLabNoteDTO): GitLabMergeRequestNoteViewModel =
    LoadedGitLabMergeRequestNoteViewModel(parentCs, note)

  private fun <T> Flow<T>.share() = shareIn(cs, SharingStarted.Lazily, 1)
}