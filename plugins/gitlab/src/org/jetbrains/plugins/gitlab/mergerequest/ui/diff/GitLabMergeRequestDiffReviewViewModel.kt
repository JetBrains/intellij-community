// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.diff

import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.filePath
import com.intellij.diff.util.Side
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestNewDiscussionPosition
import org.jetbrains.plugins.gitlab.mergerequest.data.findLatestCommitWithChangesTo
import org.jetbrains.plugins.gitlab.mergerequest.data.mapToLocation
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabPersistentMergeRequestChangesViewedState
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.mapToLocation
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabMergeRequestEditorCommentsUtil
import org.jetbrains.plugins.gitlab.mergerequest.util.toLocations

interface GitLabMergeRequestDiffReviewViewModel {
  val isCumulativeChange: Boolean

  val discussions: StateFlow<Collection<GitLabMergeRequestDiffDiscussionViewModel>>
  val draftDiscussions: StateFlow<Collection<GitLabMergeRequestDiffDraftNoteViewModel>>
  val newDiscussions: StateFlow<Collection<GitLabMergeRequestDiffNewDiscussionViewModel>>
  val locationsWithDiscussions: StateFlow<Set<DiffLineLocation>>

  val avatarIconsProvider: IconsProvider<GitLabUserDTO>

  fun requestNewDiscussion(location: DiffLineLocation, focus: Boolean)
  fun cancelNewDiscussion(location: DiffLineLocation)

  fun markViewed()
}

internal class GitLabMergeRequestDiffReviewViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  private val mergeRequest: GitLabMergeRequest,
  private val parsedChanges: GitBranchComparisonResult,
  private val diffData: GitTextFilePatchWithHistory,
  private val change: RefComparisonChange,
  private val discussionsContainer: GitLabMergeRequestDiscussionsViewModels,
  discussionsViewOption: StateFlow<DiscussionsViewOption>,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>
) : GitLabMergeRequestDiffReviewViewModel {
  private val cs = parentCs.childScope(javaClass.name)

  private val persistentChangesViewedState by lazy { project.service<GitLabPersistentMergeRequestChangesViewedState>() }

  override val isCumulativeChange: Boolean = diffData.isCumulative

  override val discussions: StateFlow<Collection<GitLabMergeRequestDiffDiscussionViewModel>> =
    discussionsContainer.discussions.map {
      it.map { GitLabMergeRequestDiffDiscussionViewModel(it, diffData, discussionsViewOption) }
    }.stateInNow(cs, emptyList())
  override val draftDiscussions: StateFlow<Collection<GitLabMergeRequestDiffDraftNoteViewModel>> =
    discussionsContainer.draftNotes.map {
      it.map { GitLabMergeRequestDiffDraftNoteViewModel(it, diffData, discussionsViewOption) }
    }.stateInNow(cs, emptyList())
  override val locationsWithDiscussions: StateFlow<Set<DiffLineLocation>> = GitLabMergeRequestEditorCommentsUtil
    .createDiscussionsPositionsFlow(mergeRequest, discussionsViewOption).toLocations {
      it.mapToLocation(diffData, Side.LEFT)
    }.stateInNow(cs, emptySet())

  override val newDiscussions: StateFlow<Collection<GitLabMergeRequestDiffNewDiscussionViewModel>> = discussionsContainer.newDiscussions.map {
    it.mapNotNull { (position, vm) ->
      val location = position.mapToLocation(diffData) ?: return@mapNotNull null
      GitLabMergeRequestDiffNewDiscussionViewModel(vm, location, discussionsViewOption)
    }
  }.stateInNow(cs, emptyList())

  override fun requestNewDiscussion(location: DiffLineLocation, focus: Boolean) {
    val position = GitLabMergeRequestNewDiscussionPosition.calcFor(diffData, location).let {
      GitLabMergeRequestDiscussionsViewModels.NewDiscussionPosition(it, location.first)
    }
    discussionsContainer.requestNewDiscussion(position, focus)
  }

  override fun cancelNewDiscussion(location: DiffLineLocation) {
    val position = GitLabMergeRequestNewDiscussionPosition.calcFor(diffData, location).let {
      GitLabMergeRequestDiscussionsViewModels.NewDiscussionPosition(it, location.first)
    }
    discussionsContainer.cancelNewDiscussion(position)
  }

  override fun markViewed() {
    val sha = parsedChanges.findLatestCommitWithChangesTo(mergeRequest.gitRepository, change.filePath) ?: return
    persistentChangesViewedState.markViewed(
      mergeRequest.glProject, mergeRequest.iid,
      mergeRequest.gitRepository,
      listOf(change.filePath to sha),
      true
    )
  }
}
