// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.*
import com.intellij.collaboration.ui.FocusableViewModel
import com.intellij.collaboration.ui.codereview.timeline.thread.CodeReviewResolvableItemViewModel
import com.intellij.collaboration.ui.codereview.timeline.thread.CodeReviewTrackableItemViewModel
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.emoji.GitLabReactionsViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiscussionViewModel.NoteItem
import org.jetbrains.plugins.gitlab.ui.GitLabMarkdownToHtmlConverter
import java.net.URL
import java.util.*

interface GitLabMergeRequestDiscussionViewModel
  : CodeReviewTrackableItemViewModel,
    FocusableViewModel,
    CodeReviewResolvableItemViewModel {
  val id: GitLabId
  val createdAt: Date

  val notes: StateFlow<List<NoteItem>>

  val replyVm: StateFlow<GitLabDiscussionReplyViewModel?>

  val position: StateFlow<GitLabNotePosition?>

  sealed interface NoteItem {
    data class Note(val vm: GitLabNoteViewModel) : NoteItem
    data class Expander(val collapsedCount: Int, val expand: () -> Unit) : NoteItem
  }
}

internal class GitLabMergeRequestDiscussionViewModelBase(
  project: Project,
  parentCs: CoroutineScope,
  projectData: GitLabProject,
  currentUser: GitLabUserDTO,
  private val discussion: GitLabMergeRequestDiscussion,
  htmlConverter: GitLabMarkdownToHtmlConverter,
) : GitLabMergeRequestDiscussionViewModel {
  private val cs = parentCs.childScope(this::class)
  private val taskLauncher = SingleCoroutineLauncher(cs)
  override val isBusy: StateFlow<Boolean> = taskLauncher.busy

  override val id: GitLabId = discussion.id
  override val trackingId: String = id.toString()
  override val createdAt: Date = discussion.createdAt

  private val expandRequested = MutableStateFlow(false)

  override val isResolved: StateFlow<Boolean> = discussion.resolved
  override val canChangeResolvedState: StateFlow<Boolean> = discussion.resolvable.mapState { it && discussion.resolveAllowed }

  override val replyVm: StateFlow<GitLabDiscussionReplyViewModel?> =
    discussion.canAddNotes.mapScoped { canAddNotes ->
      if (canAddNotes) GitLabDiscussionReplyViewModelImpl(this, project, currentUser, projectData, discussion)
      else null
    }.stateInNow(cs, null)

  private val initialNotesSize: Int = discussion.notes.value.size
  private val notesVms = discussion.notes.mapStatefulToStateful { note ->
    GitLabNoteViewModelImpl(project, this, projectData, note, discussion.notes.map { it.firstOrNull()?.id == note.id },
                            currentUser, htmlConverter)
  }.stateInNow(cs, emptyList())
  override val notes: StateFlow<List<NoteItem>> =
    combineStateIn(cs, notesVms, expandRequested) { notes, expanded ->
      if (initialNotesSize <= 3 || notes.size <= 3 || expanded) {
        notes.map { NoteItem.Note(it) }
      }
      else {
        mutableListOf(
          NoteItem.Note(notes.first()),
          NoteItem.Expander(notes.size - 2) { expandRequested.value = true },
          NoteItem.Note(notes.last())
        )
      }
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  override val position: StateFlow<GitLabNotePosition?> =
    discussion.notes.map(List<GitLabMergeRequestNote>::firstOrNull).flatMapLatest {
      it?.position ?: flowOf(null)
    }.stateInNow(cs, discussion.notes.value.firstOrNull()?.position?.value)

  override fun changeResolvedState() {
    taskLauncher.launch {
      try {
        discussion.changeResolvedState()
      }
      catch (e: Exception) {
        if (e is CancellationException) throw e
        //TODO: handle???
      }
    }
  }

  private val _focusRequestsChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
  override val focusRequests: Flow<Unit> get() = _focusRequestsChannel.receiveAsFlow()

  override fun requestFocus() {
    _focusRequestsChannel.trySend(Unit)
  }
}

class GitLabMergeRequestStandaloneDraftNoteViewModelBase internal constructor(
  project: Project,
  parentCs: CoroutineScope,
  note: GitLabMergeRequestDraftNote,
  mr: GitLabMergeRequest,
  projectData: GitLabProject,
  htmlConverter: GitLabMarkdownToHtmlConverter,
) : GitLabNoteViewModel {

  private val cs = parentCs.childScope(this::class)

  override val id: GitLabId = note.id
  override val author: GitLabUserDTO = note.author
  override val createdAt: Date? = note.createdAt
  override val isDraft: Boolean = true
  override val serverUrl: URL = mr.glProject.serverPath.toURL()

  override val actionsVm: GitLabNoteAdminActionsViewModel? =
    if (note.canAdmin) GitLabNoteAdminActionsViewModelImpl(cs, project, projectData, note) else null
  override val reactionsVm: GitLabReactionsViewModel? = null

  override val body: StateFlow<String> = note.body
  override val bodyHtml: StateFlow<String> = body.mapStateInNow(cs) {
    htmlConverter.convertToHtml(it)
  }

  override val discussionState: StateFlow<GitLabDiscussionStateContainer> =
    MutableStateFlow(GitLabDiscussionStateContainer.DEFAULT)

  val position: StateFlow<GitLabNotePosition?> = note.position

  private val _focusRequestsChannel = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)
  override val focusRequests: Flow<Unit> get() = _focusRequestsChannel.receiveAsFlow()

  override fun requestFocus() {
    _focusRequestsChannel.trySend(Unit)
  }
}