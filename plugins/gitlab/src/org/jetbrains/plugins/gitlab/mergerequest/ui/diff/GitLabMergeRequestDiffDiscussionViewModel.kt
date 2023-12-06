// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.diff

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.viewer.DiffMapped
import com.intellij.diff.util.Side
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.mergerequest.data.mapToLocation
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel

interface GitLabMergeRequestDiffDiscussionViewModel : GitLabMergeRequestDiscussionViewModel, DiffMapped

internal class GitLabMergeRequestDiffDiscussionViewModelImpl(
  base: GitLabMergeRequestDiscussionViewModel,
  diffData: GitTextFilePatchWithHistory,
  discussionsViewOption: Flow<DiscussionsViewOption>
) : GitLabMergeRequestDiscussionViewModel by base,
    GitLabMergeRequestDiffDiscussionViewModel {

  override val location: Flow<DiffLineLocation?> = base.position.map {
    it?.mapToLocation(diffData, Side.LEFT)
  }

  override val isVisible: Flow<Boolean> = combine(resolveVm?.resolved ?: flowOf(false), discussionsViewOption) { isResolved, viewOption ->
    return@combine when (viewOption) {
      DiscussionsViewOption.ALL -> true
      DiscussionsViewOption.UNRESOLVED_ONLY -> !isResolved
      DiscussionsViewOption.DONT_SHOW -> false
    }
  }
}

interface GitLabMergeRequestDiffNewDiscussionViewModel : NewGitLabNoteViewModel, DiffMapped {
  val originalLocation: DiffLineLocation
}

internal class GitLabMergeRequestEditorNewDiscussionViewModelImpl(
  base: NewGitLabNoteViewModel,
  override val originalLocation: DiffLineLocation,
  discussionsViewOption: Flow<DiscussionsViewOption>
) : NewGitLabNoteViewModel by base,
    GitLabMergeRequestDiffNewDiscussionViewModel {

  override val location: Flow<DiffLineLocation?> = flowOf(originalLocation)
  override val isVisible: Flow<Boolean> = discussionsViewOption.map { it != DiscussionsViewOption.DONT_SHOW }
}