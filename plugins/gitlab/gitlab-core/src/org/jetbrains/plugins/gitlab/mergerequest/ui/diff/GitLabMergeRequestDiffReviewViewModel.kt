// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.diff

import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.async.transformConsecutiveSuccesses
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.UnifiedCodeReviewItemPosition
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.filePath
import com.intellij.diff.util.Side
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.data.GitLabImageLoader
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestNewDiscussionPosition
import org.jetbrains.plugins.gitlab.mergerequest.data.mapToLocation
import org.jetbrains.plugins.gitlab.mergerequest.diff.GitLabMergeRequestDiffViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabPersistentMergeRequestChangesViewedState
import org.jetbrains.plugins.gitlab.mergerequest.ui.filterInFile
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.mapToLocation
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabMergeRequestDiscussionUtil
import org.jetbrains.plugins.gitlab.mergerequest.util.toLocations

interface GitLabMergeRequestDiffReviewViewModel {
  val isCumulativeChange: Boolean

  val discussions: StateFlow<ComputedResult<Collection<GitLabMergeRequestDiffDiscussionViewModel>>>
  val draftDiscussions: StateFlow<ComputedResult<Collection<GitLabMergeRequestDiffDraftNoteViewModel>>>
  val newDiscussions: StateFlow<Collection<GitLabMergeRequestDiffNewDiscussionViewModel>>

  val locationsWithDiscussions: StateFlow<Set<DiffLineLocation>>
  val locationsWithNewDiscussions: StateFlow<Set<DiffLineLocation>>

  val avatarIconsProvider: IconsProvider<GitLabUserDTO>
  val imageLoader: GitLabImageLoader

  fun nextComment(focused: String, additionalIsVisible: (String) -> Boolean): String?
  fun nextComment(cursorLocation: UnifiedCodeReviewItemPosition, additionalIsVisible: (String) -> Boolean): String?
  fun previousComment(focused: String, additionalIsVisible: (String) -> Boolean): String?
  fun previousComment(cursorLocation: UnifiedCodeReviewItemPosition, additionalIsVisible: (String) -> Boolean): String?

  fun showDiffAtComment(commentId: String)

  fun requestNewDiscussion(location: DiffLineLocation, focus: Boolean)
  fun cancelNewDiscussion(location: DiffLineLocation)

  fun markViewed()
}

internal class GitLabMergeRequestDiffReviewViewModelImpl(
  project: Project,
  parentCs: CoroutineScope,
  private val mergeRequest: GitLabMergeRequest,
  private val diffData: GitTextFilePatchWithHistory,
  private val change: RefComparisonChange,
  private val diffVm: GitLabMergeRequestDiffViewModel,
  private val discussionsContainer: GitLabMergeRequestDiscussionsViewModels,
  discussionsViewOption: StateFlow<DiscussionsViewOption>,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  override val imageLoader: GitLabImageLoader
) : GitLabMergeRequestDiffReviewViewModel {
  private val cs = parentCs.childScope(javaClass.name)

  private val persistentChangesViewedState by lazy { project.service<GitLabPersistentMergeRequestChangesViewedState>() }

  override val isCumulativeChange: Boolean = diffData.isCumulative

  override val discussions: StateFlow<ComputedResult<Collection<GitLabMergeRequestDiffDiscussionViewModel>>> =
    diffVm.discussions
      .transformConsecutiveSuccesses { filterInFile(change) }
      .stateInNow(cs, ComputedResult.loading())
  override val draftDiscussions: StateFlow<ComputedResult<Collection<GitLabMergeRequestDiffDraftNoteViewModel>>> =
    diffVm.draftDiscussions
      .transformConsecutiveSuccesses { filterInFile(change) }
      .stateInNow(cs, ComputedResult.loading())
  override val newDiscussions: StateFlow<Collection<GitLabMergeRequestDiffNewDiscussionViewModel>> = discussionsContainer.newDiscussions.map {
    it.mapNotNull { (position, vm) ->
      val location = position.mapToLocation(diffData) ?: return@mapNotNull null
      GitLabMergeRequestDiffNewDiscussionViewModel(vm, location, discussionsViewOption)
    }
  }.stateInNow(cs, emptyList())

  override val locationsWithDiscussions: StateFlow<Set<DiffLineLocation>> = GitLabMergeRequestDiscussionUtil
    .createDiscussionsPositionsFlow(mergeRequest, discussionsViewOption).toLocations {
      it.mapToLocation(diffData, Side.LEFT)
    }.stateInNow(cs, emptySet())

  @OptIn(ExperimentalCoroutinesApi::class)
  override val locationsWithNewDiscussions: StateFlow<Set<DiffLineLocation>> =
    discussionsContainer.newDiscussions
      .map {
        it.keys.mapNotNullTo(mutableSetOf()) {
          it.mapToLocation(diffData) ?: return@mapNotNullTo null
        }
      }
      .stateInNow(cs, emptySet())

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
    val sha = mergeRequest.details.value.diffRefs?.headSha ?: return
    persistentChangesViewedState.markViewed(
      mergeRequest.glProject, mergeRequest.iid,
      mergeRequest.gitRepository,
      listOf(change.filePath to sha),
      true
    )
  }

  override fun showDiffAtComment(commentId: String) {
    diffVm.showDiffAtComment(commentId)
  }

  override fun nextComment(focused: String, additionalIsVisible: (String) -> Boolean): String? =
    diffVm.findNextComment(focused, additionalIsVisible)

  override fun nextComment(cursorLocation: UnifiedCodeReviewItemPosition, additionalIsVisible: (String) -> Boolean): String? =
    diffVm.findNextComment(cursorLocation, additionalIsVisible)

  override fun previousComment(focused: String, additionalIsVisible: (String) -> Boolean): String? =
    diffVm.findPreviousComment(focused, additionalIsVisible)

  override fun previousComment(cursorLocation: UnifiedCodeReviewItemPosition, additionalIsVisible: (String) -> Boolean): String? =
    diffVm.findPreviousComment(cursorLocation, additionalIsVisible)
}
