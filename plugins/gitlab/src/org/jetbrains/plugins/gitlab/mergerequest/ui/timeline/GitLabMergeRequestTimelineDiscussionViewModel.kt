// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.mapScoped
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabDiscussion
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNote
import org.jetbrains.plugins.gitlab.mergerequest.ui.comment.GitLabMergeRequestDiscussionResolveViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.comment.GitLabMergeRequestDiscussionResolveViewModelImpl
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModelImpl

interface GitLabMergeRequestTimelineDiscussionViewModel {
  val author: Flow<GitLabUserDTO>

  val mainNote: Flow<GitLabNoteViewModel>
  val replies: Flow<List<GitLabNoteViewModel>>

  val repliesFolded: Flow<Boolean>

  val resolvedVm: GitLabMergeRequestDiscussionResolveViewModel?

  fun setRepliesFolded(folded: Boolean)
}

class GitLabMergeRequestTimelineDiscussionViewModelImpl(
  parentCs: CoroutineScope,
  discussion: GitLabDiscussion
) : GitLabMergeRequestTimelineDiscussionViewModel {

  private val cs = parentCs.childScope()

  override val mainNote: Flow<GitLabNoteViewModel> = discussion.notes.mapScoped {
    createNoteVm(this, it.first())
  }.share()

  private val _repliesFolded = MutableStateFlow(true)
  override val repliesFolded: Flow<Boolean> = _repliesFolded.asStateFlow()

  override val replies: Flow<List<GitLabNoteViewModel>> =
    discussion.notes.mapScoped { notesList ->
      notesList.asSequence().drop(1).map { createNoteVm(this, it) }.toList()
    }.share()

  override val author: Flow<GitLabUserDTO> = mainNote.map { it.author }

  override val resolvedVm: GitLabMergeRequestDiscussionResolveViewModel? =
    if (discussion.canResolve) GitLabMergeRequestDiscussionResolveViewModelImpl(cs, discussion) else null

  override fun setRepliesFolded(folded: Boolean) {
    _repliesFolded.value = folded
  }

  private fun createNoteVm(parentCs: CoroutineScope, note: GitLabNote): GitLabNoteViewModel =
    GitLabNoteViewModelImpl(parentCs, note)

  private fun <T> Flow<T>.share() = shareIn(cs, SharingStarted.Lazily, 1)
}