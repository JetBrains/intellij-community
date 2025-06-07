// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapModelsToViewModels
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.timeline.CollapsibleTimelineItemViewModel
import com.intellij.collaboration.ui.codereview.timeline.thread.CodeReviewFoldableThreadViewModel
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
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDiscussion
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteEditingViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModelImpl
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel
import java.net.URL

interface GitLabMergeRequestTimelineDiscussionViewModel :
  CodeReviewFoldableThreadViewModel,
  CodeReviewResolvableItemViewModel,
  CollapsibleTimelineItemViewModel {
  val id: String
  val serverUrl: URL

  val author: Flow<GitLabUserDTO>

  val diffVm: Flow<GitLabDiscussionDiffViewModel?>

  val mainNote: Flow<GitLabNoteViewModel>
  val replies: StateFlow<List<GitLabNoteViewModel>>

  val replyVm: StateFlow<NewGitLabNoteViewModel?>
}

private val LOG = logger<GitLabMergeRequestTimelineDiscussionViewModel>()

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabMergeRequestTimelineDiscussionViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  projectData: GitLabProject,
  currentUser: GitLabUserDTO,
  private val mr: GitLabMergeRequest,
  private val discussion: GitLabMergeRequestDiscussion
) : GitLabMergeRequestTimelineDiscussionViewModel {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })
  private val taskLauncher = SingleCoroutineLauncher(cs)

  override val mainNote: Flow<GitLabNoteViewModel> = discussion.notes
    .map { it.first() }
    .distinctUntilChangedBy { it.id }
    .mapScoped { GitLabNoteViewModelImpl(project, this, projectData, it, flowOf(true), currentUser) }
    .modelFlow(cs, LOG)

  override val id: String = discussion.id.toString()
  override val serverUrl: URL = mr.glProject.serverPath.toURL()
  override val author: Flow<GitLabUserDTO> = mainNote.map { it.author }

  private val _repliesFolded = MutableStateFlow(true)
  override val repliesFolded: StateFlow<Boolean> = _repliesFolded.asStateFlow()

  override val repliesState: StateFlow<CodeReviewFoldableThreadViewModel.RepliesStateData> = discussion.notes.map { notes ->
    val replies = notes.drop(1)
    CodeReviewFoldableThreadViewModel.RepliesStateData.Default(
      replies.mapTo(mutableSetOf()) { it.author },
      replies.size,
      replies.lastOrNull()?.createdAt
    )
  }.stateIn(cs, SharingStarted.Eagerly, CodeReviewFoldableThreadViewModel.RepliesStateData.Empty)

  override val replies: StateFlow<List<GitLabNoteViewModel>> = discussion.notes
    .map { it.drop(1) }
    .mapModelsToViewModels { GitLabNoteViewModelImpl(project, this, projectData, it, flowOf(false), currentUser) }
    .stateIn(cs, SharingStarted.Lazily, listOf())

  override val isBusy: StateFlow<Boolean> = taskLauncher.busy

  override val isResolved: StateFlow<Boolean> = discussion.resolved
  override val canChangeResolvedState: StateFlow<Boolean> = MutableStateFlow(discussion.canResolve)

  override val collapsible: Flow<Boolean> = isResolved

  private val _collapsed: MutableStateFlow<Boolean> = MutableStateFlow(true)
  override val collapsed: Flow<Boolean> = combine(collapsible, _collapsed) { collapsible, collapsed -> collapsible && collapsed }

  override val canCreateReplies: StateFlow<Boolean> = discussion.canAddNotes.stateIn(cs, SharingStarted.Eagerly, false)
  override val replyVm: StateFlow<NewGitLabNoteViewModel?> =
    discussion.canAddNotes.mapScoped { canAddNotes ->
      if (canAddNotes) GitLabNoteEditingViewModel.forReplyNote(this, project, discussion, currentUser) else null
    }.stateIn(cs, SharingStarted.Eagerly, null)

  override val diffVm: Flow<GitLabDiscussionDiffViewModel?> =
    discussion.notes
      .flatMapLatest { it.firstOrNull()?.position ?: flowOf() }
      .distinctUntilChanged()
      .mapScoped { pos -> pos?.let { GitLabDiscussionDiffViewModelImpl(this, mr, it) } }
      .modelFlow(cs, LOG)

  init {
    cs.launchNow {
      discussion.resolved.collect {
        setCollapsed(it)
      }
    }
  }

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

  override fun setCollapsed(collapsed: Boolean) {
    _collapsed.value = collapsed
    if (collapsed) {
      _repliesFolded.value = true
    }
  }

  override fun unfoldReplies() {
    _repliesFolded.value = false
    _collapsed.value = false
    replyVm.value?.requestFocus()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitLabMergeRequestTimelineDiscussionViewModelImpl) return false

    if (id != other.id) return false

    return true
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }
}