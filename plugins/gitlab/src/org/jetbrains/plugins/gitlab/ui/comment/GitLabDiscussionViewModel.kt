// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.mapCaching
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabDiscussion
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNote
import org.jetbrains.plugins.gitlab.ui.comment.GitLabDiscussionViewModel.NoteItem

interface GitLabDiscussionViewModel {
  val id: String
  val notes: Flow<List<NoteItem>>

  val resolveVm: GitLabDiscussionResolveViewModel?
  val replyVm: GitLabDiscussionReplyViewModel?

  suspend fun destroy()

  sealed interface NoteItem {
    val id: Any

    class Note(val vm: GitLabNoteViewModel) : NoteItem {
      override val id: Any = vm.id
    }

    class Expander(val collapsedCount: Int, val expand: () -> Unit) : NoteItem {
      override val id: Any = "EXPANDER$collapsedCount"
    }
  }
}

private val LOG = logger<GitLabDiscussionViewModel>()

class GitLabDiscussionViewModelImpl(
  parentCs: CoroutineScope,
  currentUser: GitLabUserDTO,
  discussion: GitLabDiscussion
) : GitLabDiscussionViewModel {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  override val id: String = discussion.id

  private val expandRequested = MutableStateFlow(false)

  override val resolveVm: GitLabDiscussionResolveViewModel? =
    if (discussion.canResolve) GitLabDiscussionResolveViewModelImpl(cs, discussion) else null

  override val replyVm: GitLabDiscussionReplyViewModel? =
    if (discussion.canAddNotes) GitLabDiscussionReplyViewModelImpl(cs, currentUser, discussion) else null

  // this is NOT a good way to do this, but a proper implementation would be waaaay too convoluted
  @Volatile
  private var initialNotesSize: Int? = null
  override val notes: Flow<List<NoteItem>> = discussion.notes.onEach {
    if (initialNotesSize == null) {
      initialNotesSize = it.size
    }
  }.mapCaching(
    GitLabNote::id,
    { cs, note -> GitLabNoteViewModelImpl(cs, note, getDiscussionState(discussion, note)) },
    GitLabNoteViewModelImpl::destroy
  ).combine(expandRequested) { notes, expanded ->
    if (initialNotesSize!! <= 3 || notes.size <= 3 || expanded) {
      notes.map { NoteItem.Note(it) }
    }
    else {
      mutableListOf(
        NoteItem.Note(notes.first()),
        NoteItem.Expander(notes.size - 2) { expandRequested.value = true },
        NoteItem.Note(notes.last())
      )
    }
  }.modelFlow(cs, LOG)

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun getDiscussionState(discussion: GitLabDiscussion, note: GitLabNote): Flow<GitLabDiscussionStateContainer> =
    discussion.notes.map { it.firstOrNull()?.id == note.id }.mapLatest {
      if (it) GitLabDiscussionStateContainer(discussion.resolved) else GitLabDiscussionStateContainer(flowOf(false))
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