// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.cancelAndJoinSilently
import com.intellij.collaboration.async.mapCaching
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.viewer.DiffMapped
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.*
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiffDiscussionViewModel.NoteItem

interface GitLabMergeRequestDiffDiscussionViewModel : DiffMapped {
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

private val LOG = logger<GitLabMergeRequestDiffDiscussionViewModel>()

class GitLabMergeRequestDiffDiscussionViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  diffData: GitTextFilePatchWithHistory,
  currentUser: GitLabUserDTO,
  discussion: GitLabMergeRequestDiscussion,
  discussionsViewOption: Flow<DiscussionsViewOption>,
  glProject: GitLabProjectCoordinates
) : GitLabMergeRequestDiffDiscussionViewModel {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  override val id: String = discussion.id

  private val expandRequested = MutableStateFlow(false)

  override val resolveVm: GitLabDiscussionResolveViewModel? =
    if (discussion.resolvable) GitLabDiscussionResolveViewModelImpl(cs, discussion) else null

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
    { note -> GitLabNoteViewModelImpl(project, this, note, discussion.notes.map { it.firstOrNull()?.id == note.id }, glProject) },
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
  override val location: Flow<DiffLineLocation?> = discussion.firstNote().flatMapLatest {
    it?.position ?: flowOf(null)
  }.distinctUntilChanged().map {
    if (it == null) return@map null
    it.mapToLocation(diffData)
  }

  override val isVisible: Flow<Boolean> = combine(resolveVm?.resolved ?: flowOf(false), discussionsViewOption) { isResolved, viewOption ->
    return@combine when (viewOption) {
      DiscussionsViewOption.ALL -> true
      DiscussionsViewOption.UNRESOLVED_ONLY -> !isResolved
      DiscussionsViewOption.DONT_SHOW -> false
    }
  }

  private fun GitLabMergeRequestDiscussion.firstNote(): Flow<GitLabMergeRequestNote?> =
    notes.map(List<GitLabMergeRequestNote>::firstOrNull).distinctUntilChangedBy { it?.id }

  override suspend fun destroy() = cs.cancelAndJoinSilently()
}

class GitLabMergeRequestDiffDraftDiscussionViewModel(
  project: Project,
  parentCs: CoroutineScope,
  diffData: GitTextFilePatchWithHistory,
  note: GitLabMergeRequestDraftNote,
  glProject: GitLabProjectCoordinates
) : GitLabMergeRequestDiffDiscussionViewModel {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  override val id: String = note.id

  override val notes: Flow<List<NoteItem>> = flowOf(
    listOf(NoteItem.Note(GitLabNoteViewModelImpl(project, cs, note, flowOf(true), glProject)))
  )

  override val location: Flow<DiffLineLocation?> = note.position.map {
    if (it == null) return@map null
    it.mapToLocation(diffData)
  }

  override val isVisible: Flow<Boolean> = flowOf(true)

  override val resolveVm: GitLabDiscussionResolveViewModel? = null
  override val replyVm: GitLabDiscussionReplyViewModel? = null

  override suspend fun destroy() = cs.cancelAndJoinSilently()
}