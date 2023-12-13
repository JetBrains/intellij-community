// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.mapModelsToViewModels
import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.modelFlow
import com.intellij.collaboration.ui.codereview.timeline.CollapsibleTimelineItemViewModel
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDiscussion
import org.jetbrains.plugins.gitlab.ui.comment.*
import java.net.URL

interface GitLabMergeRequestTimelineDiscussionViewModel :
  CollapsibleTimelineItemViewModel {
  val id: String
  val serverUrl: URL

  val author: Flow<GitLabUserDTO>

  val diffVm: Flow<GitLabDiscussionDiffViewModel?>

  val mainNote: Flow<GitLabNoteViewModel>
  val replies: Flow<List<GitLabNoteViewModel>>

  val repliesFolded: Flow<Boolean>

  val resolveVm: GitLabDiscussionResolveViewModel?
  val replyVm: StateFlow<NewGitLabNoteViewModel?>

  fun setRepliesFolded(folded: Boolean)
}

private val LOG = logger<GitLabMergeRequestTimelineDiscussionViewModel>()

@OptIn(ExperimentalCoroutinesApi::class)
class GitLabMergeRequestTimelineDiscussionViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  currentUser: GitLabUserDTO,
  private val mr: GitLabMergeRequest,
  discussion: GitLabMergeRequestDiscussion
) : GitLabMergeRequestTimelineDiscussionViewModel {

  private val cs = parentCs.childScope(CoroutineExceptionHandler { _, e -> LOG.warn(e) })

  override val mainNote: Flow<GitLabNoteViewModel> = discussion.notes
    .map { it.first() }
    .distinctUntilChangedBy { it.id }
    .mapScoped { GitLabNoteViewModelImpl(project, this, it, flowOf(true), mr.glProject) }
    .modelFlow(cs, LOG)

  override val id: String = discussion.id.toString()
  override val serverUrl: URL = mr.glProject.serverPath.toURL()
  override val author: Flow<GitLabUserDTO> = mainNote.map { it.author }

  private val _repliesFolded = MutableStateFlow(true)
  override val repliesFolded: Flow<Boolean> = _repliesFolded.asStateFlow()

  override val replies: Flow<List<GitLabNoteViewModel>> = discussion.notes
    .map { it.drop(1) }
    .mapModelsToViewModels { GitLabNoteViewModelImpl(project, this, it, flowOf(false), mr.glProject) }
    .modelFlow(cs, LOG)

  override val resolveVm: GitLabDiscussionResolveViewModel? =
    if (discussion.resolvable) GitLabDiscussionResolveViewModelImpl(cs, discussion) else null

  override val collapsible: Flow<Boolean> = resolveVm?.resolved ?: flowOf(false)

  private val _collapsed: MutableStateFlow<Boolean> = MutableStateFlow(true)
  override val collapsed: Flow<Boolean> = combine(collapsible, _collapsed) { collapsible, collapsed -> collapsible && collapsed }

  override val replyVm: StateFlow<NewGitLabNoteViewModel?> =
    discussion.canAddNotes.mapScoped { canAddNotes ->
      if (canAddNotes) GitLabNoteEditingViewModel.forReplyNote(this, project, discussion, currentUser) else null
    }.stateIn(cs, SharingStarted.Eagerly, null)

  override val diffVm: Flow<GitLabDiscussionDiffViewModel?> =
    discussion.notes
      .flatMapLatest { it.first().position }
      .distinctUntilChanged()
      .mapScoped { pos -> pos?.let { GitLabDiscussionDiffViewModelImpl(this, mr, it) } }
      .modelFlow(cs, LOG)

  init {
    val resolvedFlow = resolveVm?.resolved
    if (resolvedFlow != null) {
      cs.launch(start = CoroutineStart.UNDISPATCHED) {
        resolvedFlow.collect {
          setCollapsed(it)
        }
      }
    }
  }

  override fun setCollapsed(collapsed: Boolean) {
    _collapsed.value = collapsed
    if (collapsed) {
      _repliesFolded.value = true
    }
  }

  override fun setRepliesFolded(folded: Boolean) {
    _repliesFolded.value = folded
    if (!folded) {
      _collapsed.value = false
      replyVm.value?.requestFocus()
    }
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