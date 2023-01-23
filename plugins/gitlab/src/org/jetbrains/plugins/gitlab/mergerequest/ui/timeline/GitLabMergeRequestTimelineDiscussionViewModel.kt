// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.mapCaching
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabDiscussion
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNote
import org.jetbrains.plugins.gitlab.mergerequest.ui.comment.GitLabMergeRequestDiscussionResolveViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.comment.GitLabMergeRequestDiscussionResolveViewModelImpl
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModelImpl
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModelImpl
import java.util.*

interface GitLabMergeRequestTimelineDiscussionViewModel {
  val date: Date
  val author: Flow<GitLabUserDTO>

  val mainNote: Flow<GitLabNoteViewModel>
  val replies: Flow<List<GitLabNoteViewModel>>

  val repliesFolded: Flow<Boolean>

  val resolveVm: GitLabMergeRequestDiscussionResolveViewModel?
  val newNoteVm: NewGitLabNoteViewModel?

  fun setRepliesFolded(folded: Boolean)

  suspend fun destroy()
}

private val LOG = logger<GitLabMergeRequestTimelineDiscussionViewModel>()

class GitLabMergeRequestTimelineDiscussionViewModelImpl(
  parentCs: CoroutineScope,
  currentUser: GitLabUserDTO,
  discussion: GitLabDiscussion
) : GitLabMergeRequestTimelineDiscussionViewModel {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  override val mainNote: Flow<GitLabNoteViewModel> = discussion.notes
    .map { it.first() }
    .distinctUntilChangedBy { it.id }
    .mapScoped { GitLabNoteViewModelImpl(this, it) }
    .modelFlow(cs, LOG)

  override val date: Date = discussion.createdAt
  override val author: Flow<GitLabUserDTO> = mainNote.map { it.author }

  private val _repliesFolded = MutableStateFlow(true)
  override val repliesFolded: Flow<Boolean> = _repliesFolded.asStateFlow()

  override val replies: Flow<List<GitLabNoteViewModel>> = discussion.notes
    .map { it.drop(1) }
    .mapCaching(
      GitLabNote::id,
      { GitLabNoteViewModelImpl(cs, it) },
      GitLabNoteViewModelImpl::destroy
    )
    .modelFlow(cs, LOG)

  override val resolveVm: GitLabMergeRequestDiscussionResolveViewModel? =
    if (discussion.canResolve) GitLabMergeRequestDiscussionResolveViewModelImpl(cs, discussion) else null

  override val newNoteVm: NewGitLabNoteViewModel? =
    if(discussion.canAddNotes) NewGitLabNoteViewModelImpl(cs, currentUser, discussion) else null

  override fun setRepliesFolded(folded: Boolean) {
    _repliesFolded.value = folded
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