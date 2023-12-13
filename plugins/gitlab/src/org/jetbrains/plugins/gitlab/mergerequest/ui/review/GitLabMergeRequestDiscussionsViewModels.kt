// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.review

import com.intellij.collaboration.async.*
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestNewDiscussionPosition
import org.jetbrains.plugins.gitlab.mergerequest.data.mapToLocation
import org.jetbrains.plugins.gitlab.ui.comment.*

private typealias DiscussionsFlow = Flow<Collection<GitLabMergeRequestDiscussionViewModel>>
private typealias DraftNotesFlow = Flow<Collection<GitLabMergeRequestStandaloneDraftNoteViewModelBase>>
private typealias NewDiscussionsFlow = Flow<Map<GitLabMergeRequestDiscussionsViewModels.NewDiscussionPosition, NewGitLabNoteViewModel>>

interface GitLabMergeRequestDiscussionsViewModels {
  val discussions: DiscussionsFlow
  val draftNotes: DraftNotesFlow
  val newDiscussions: NewDiscussionsFlow

  fun requestNewDiscussion(position: NewDiscussionPosition, focus: Boolean)
  fun cancelNewDiscussion(position: NewDiscussionPosition)

  class NewDiscussionPosition(val position: GitLabMergeRequestNewDiscussionPosition, val side: Side) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as NewDiscussionPosition

      return position == other.position
    }

    override fun hashCode(): Int = position.hashCode()
  }
}

fun GitLabMergeRequestDiscussionsViewModels.NewDiscussionPosition.mapToLocation(diffData: GitTextFilePatchWithHistory): DiffLineLocation? =
  position.mapToLocation(diffData, side)

private val LOG = logger<GitLabMergeRequestDiscussionsViewModelsImpl>()

internal class GitLabMergeRequestDiscussionsViewModelsImpl(
  private val project: Project,
  parentCs: CoroutineScope,
  private val currentUser: GitLabUserDTO,
  private val mergeRequest: GitLabMergeRequest
) : GitLabMergeRequestDiscussionsViewModels {

  private val cs = parentCs.childScope(Dispatchers.Default + CoroutineName("GitLab Merge Request Review Discussions"))

  override val discussions: DiscussionsFlow = mergeRequest.discussions
    .throwFailure()
    .mapModelsToViewModels { GitLabMergeRequestDiscussionViewModelBase(project, this, currentUser, it, mergeRequest.glProject) }
    .modelFlow(cs, LOG)

  override val draftNotes: DraftNotesFlow = mergeRequest.draftNotes
    .throwFailure()
    .mapFiltered { it.discussionId == null }
    .mapModelsToViewModels { GitLabMergeRequestStandaloneDraftNoteViewModelBase(project, this, it, mergeRequest.glProject) }
    .modelFlow(cs, LOG)


  private val _newDiscussions = MutableStateFlow<Map<GitLabMergeRequestDiscussionsViewModels.NewDiscussionPosition, NewGitLabNoteViewModel>>(
    emptyMap())
  override val newDiscussions: NewDiscussionsFlow = _newDiscussions.asStateFlow()

  override fun requestNewDiscussion(position: GitLabMergeRequestDiscussionsViewModels.NewDiscussionPosition, focus: Boolean) {
    _newDiscussions.updateAndGet { currentNewDiscussions ->
      if (!currentNewDiscussions.containsKey(position) && mergeRequest.canAddNotes) {
        val vm = GitLabNoteEditingViewModel.forNewDiffNote(cs, project, mergeRequest, currentUser, position.position).apply {
          onDoneIn(cs) {
            cancelNewDiscussion(position)
          }
        }
        currentNewDiscussions + (position to vm)
      }
      else {
        currentNewDiscussions
      }
    }.apply {
      if (focus) {
        get(position)?.requestFocus()
      }
    }
  }

  override fun cancelNewDiscussion(position: GitLabMergeRequestDiscussionsViewModels.NewDiscussionPosition) {
    _newDiscussions.update {
      val oldVm = it[position]
      val newMap = it - position
      cs.launch {
        oldVm?.destroy()
      }
      newMap
    }
  }

  suspend fun destroy() = cs.cancelAndJoinSilently()
}