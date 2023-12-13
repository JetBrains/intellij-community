// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.diff

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.icon.IconsProvider
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestNewDiscussionPosition
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.mapToLocation

interface GitLabMergeRequestDiffReviewViewModel {
  val isCumulativeChange: Boolean

  val canComment: Flow<Boolean>

  val discussions: Flow<Collection<GitLabMergeRequestDiffDiscussionViewModel>>
  val draftDiscussions: Flow<Collection<GitLabMergeRequestDiffDraftNoteViewModel>>
  val newDiscussions: Flow<Collection<GitLabMergeRequestDiffNewDiscussionViewModel>>

  val avatarIconsProvider: IconsProvider<GitLabUserDTO>

  fun requestNewDiscussion(location: DiffLineLocation, focus: Boolean)
  fun cancelNewDiscussion(location: DiffLineLocation)
}

internal class GitLabMergeRequestDiffReviewViewModelImpl(
  private val diffData: GitTextFilePatchWithHistory,
  private val discussionsContainer: GitLabMergeRequestDiscussionsViewModels,
  discussionsViewOption: Flow<DiscussionsViewOption>,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>
) : GitLabMergeRequestDiffReviewViewModel {

  override val isCumulativeChange: Boolean = diffData.isCumulative

  override val discussions: Flow<Collection<GitLabMergeRequestDiffDiscussionViewModel>> =
    discussionsContainer.discussions.map {
      it.map { GitLabMergeRequestDiffDiscussionViewModel(it, diffData, discussionsViewOption) }
    }
  override val draftDiscussions: Flow<Collection<GitLabMergeRequestDiffDraftNoteViewModel>> =
    discussionsContainer.draftNotes.map {
      it.map { GitLabMergeRequestDiffDraftNoteViewModel(it, diffData, discussionsViewOption) }
    }

  override val canComment: Flow<Boolean> = discussionsViewOption.map { it != DiscussionsViewOption.DONT_SHOW }
  override val newDiscussions: Flow<Collection<GitLabMergeRequestDiffNewDiscussionViewModel>> = discussionsContainer.newDiscussions.map {
    it.mapNotNull { (position, vm) ->
      val location = position.mapToLocation(diffData) ?: return@mapNotNull null
      GitLabMergeRequestDiffNewDiscussionViewModel(vm, location, discussionsViewOption)
    }
  }

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
}
