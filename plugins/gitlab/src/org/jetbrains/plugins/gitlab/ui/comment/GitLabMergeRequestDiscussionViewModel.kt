// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.mapModelsToViewModels
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.*
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiscussionViewModel.NoteItem
import java.net.URL
import java.util.*

interface GitLabMergeRequestDiscussionViewModel {
  val id: GitLabId
  val notes: Flow<List<NoteItem>>

  val resolveVm: GitLabDiscussionResolveViewModel?
  val replyVm: Flow<GitLabDiscussionReplyViewModel?>

  val position: Flow<GitLabNotePosition?>

  sealed interface NoteItem {
    data class Note(val vm: GitLabNoteViewModel) : NoteItem
    data class Expander(val collapsedCount: Int, val expand: () -> Unit) : NoteItem
  }
}

private val LOG = logger<GitLabMergeRequestDiscussionViewModel>()

internal class GitLabMergeRequestDiscussionViewModelBase(
  project: Project,
  parentCs: CoroutineScope,
  currentUser: GitLabUserDTO,
  discussion: GitLabMergeRequestDiscussion,
  glProject: GitLabProjectCoordinates
) : GitLabMergeRequestDiscussionViewModel {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  override val id: GitLabId = discussion.id

  private val expandRequested = MutableStateFlow(false)

  override val resolveVm: GitLabDiscussionResolveViewModel? =
    if (discussion.resolvable) GitLabDiscussionResolveViewModelImpl(cs, discussion) else null

  override val replyVm: Flow<GitLabDiscussionReplyViewModel?> =
    discussion.canAddNotes.mapScoped { canAddNotes ->
      if (canAddNotes) GitLabDiscussionReplyViewModelImpl(this, project, currentUser, discussion)
      else null
    }.modelFlow(cs, LOG)

  // this is NOT a good way to do this, but a proper implementation would be waaaay too convoluted
  @Volatile
  private var initialNotesSize: Int? = null
  override val notes: Flow<List<NoteItem>> = discussion.notes.onEach {
    if (initialNotesSize == null) {
      initialNotesSize = it.size
    }
  }.mapModelsToViewModels { note ->
    GitLabNoteViewModelImpl(project, this, note, discussion.notes.map { it.firstOrNull()?.id == note.id }, glProject)
  }.combine(expandRequested) { notes, expanded ->
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
  override val position: Flow<GitLabNotePosition?> = discussion.firstNote().flatMapLatest {
    it?.position ?: flowOf(null)
  }.distinctUntilChanged().modelFlow(cs, LOG)

  private fun GitLabMergeRequestDiscussion.firstNote(): Flow<GitLabMergeRequestNote?> =
    notes.map(List<GitLabMergeRequestNote>::firstOrNull).distinctUntilChangedBy { it?.id }
}

class GitLabMergeRequestStandaloneDraftNoteViewModelBase internal constructor(
  project: Project,
  parentCs: CoroutineScope,
  note: GitLabMergeRequestDraftNote,
  glProject: GitLabProjectCoordinates
) : GitLabNoteViewModel {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  override val id: GitLabId = note.id
  override val author: GitLabUserDTO = note.author
  override val createdAt: Date? = note.createdAt
  override val isDraft: Boolean = true
  override val serverUrl: URL = glProject.serverPath.toURL()

  override val actionsVm: GitLabNoteAdminActionsViewModel? =
    if (note.canAdmin) GitLabNoteAdminActionsViewModelImpl(cs, project, note) else null

  override val body: Flow<String> = note.body
  override val bodyHtml: Flow<String> = body.map { GitLabUIUtil.convertToHtml(project, it) }.modelFlow(cs, LOG)

  override val discussionState: Flow<GitLabDiscussionStateContainer> = flowOf(GitLabDiscussionStateContainer.DEFAULT)

  val position: Flow<GitLabNotePosition?> = note.position
}