// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.mapModelsToViewModels
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.ui.codereview.timeline.thread.CodeReviewResolvableItemViewModel
import com.intellij.collaboration.util.SingleCoroutineLauncher
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.GitLabId
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.emoji.GitLabReactionsViewModel
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiscussionViewModel.NoteItem
import java.net.URL
import java.util.*

interface GitLabMergeRequestDiscussionViewModel : CodeReviewResolvableItemViewModel {
  val id: GitLabId
  val notes: Flow<List<NoteItem>>

  val replyVm: Flow<GitLabDiscussionReplyViewModel?>

  val position: StateFlow<GitLabNotePosition?>

  sealed interface NoteItem {
    data class Note(val vm: GitLabNoteViewModel) : NoteItem
    data class Expander(val collapsedCount: Int, val expand: () -> Unit) : NoteItem
  }
}

private val LOG = logger<GitLabMergeRequestDiscussionViewModel>()

internal class GitLabMergeRequestDiscussionViewModelBase(
  project: Project,
  parentCs: CoroutineScope,
  projectData: GitLabProject,
  currentUser: GitLabUserDTO,
  private val discussion: GitLabMergeRequestDiscussion
) : GitLabMergeRequestDiscussionViewModel {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })
  private val taskLauncher = SingleCoroutineLauncher(cs)
  override val isBusy: StateFlow<Boolean> = taskLauncher.busy

  override val id: GitLabId = discussion.id

  private val expandRequested = MutableStateFlow(false)

  override val isResolved: StateFlow<Boolean> = discussion.resolved
  override val canChangeResolvedState: StateFlow<Boolean> = MutableStateFlow(discussion.canResolve)

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
    GitLabNoteViewModelImpl(project, this, projectData, note, discussion.notes.map { it.firstOrNull()?.id == note.id }, currentUser)
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
}

class GitLabMergeRequestStandaloneDraftNoteViewModelBase internal constructor(
  project: Project,
  parentCs: CoroutineScope,
  note: GitLabMergeRequestDraftNote,
  mr: GitLabMergeRequest
) : GitLabNoteViewModel {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  override val id: GitLabId = note.id
  override val author: GitLabUserDTO = note.author
  override val createdAt: Date? = note.createdAt
  override val isDraft: Boolean = true
  override val serverUrl: URL = mr.glProject.serverPath.toURL()

  override val actionsVm: GitLabNoteAdminActionsViewModel? =
    if (note.canAdmin) GitLabNoteAdminActionsViewModelImpl(cs, project, note) else null
  override val reactionsVm: GitLabReactionsViewModel? = null

  override val body: Flow<String> = note.body
  override val bodyHtml: Flow<String> = body.map {
    GitLabUIUtil.convertToHtml(project, mr.gitRepository, mr.glProject.projectPath, it)
  }.modelFlow(cs, LOG)

  override val discussionState: Flow<GitLabDiscussionStateContainer> = flowOf(GitLabDiscussionStateContainer.DEFAULT)

  val position: StateFlow<GitLabNotePosition?> = note.position
}