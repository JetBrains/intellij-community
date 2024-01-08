// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.diff

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.viewer.DiffMapped
import com.intellij.diff.util.Side
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.gitlab.mergerequest.data.mapToLocation
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestStandaloneDraftNoteViewModelBase
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel

class GitLabMergeRequestDiffDiscussionViewModel internal constructor(
  base: GitLabMergeRequestDiscussionViewModel,
  diffData: GitTextFilePatchWithHistory,
  discussionsViewOption: StateFlow<DiscussionsViewOption>
) : GitLabMergeRequestDiscussionViewModel by base,
    DiffMapped {

  override val location: StateFlow<DiffLineLocation?> = base.position.mapState {
    it?.mapToLocation(diffData, Side.LEFT)
  }

  override val isVisible: StateFlow<Boolean> = isResolved.combineState(discussionsViewOption) { isResolved, viewOption ->
    when (viewOption) {
      DiscussionsViewOption.ALL -> true
      DiscussionsViewOption.UNRESOLVED_ONLY -> !isResolved
      DiscussionsViewOption.DONT_SHOW -> false
    }
  }
}

class GitLabMergeRequestDiffDraftNoteViewModel internal constructor(
  base: GitLabMergeRequestStandaloneDraftNoteViewModelBase,
  diffData: GitTextFilePatchWithHistory,
  discussionsViewOption: StateFlow<DiscussionsViewOption>
) : GitLabNoteViewModel by base, DiffMapped {

  override val location: StateFlow<DiffLineLocation?> = base.position.mapState {
    it?.mapToLocation(diffData, Side.LEFT)
  }

  override val isVisible: StateFlow<Boolean> = discussionsViewOption.mapState {
    when (it) {
      DiscussionsViewOption.UNRESOLVED_ONLY, DiscussionsViewOption.ALL -> true
      DiscussionsViewOption.DONT_SHOW -> false
    }
  }
}

class GitLabMergeRequestDiffNewDiscussionViewModel internal constructor(
  base: NewGitLabNoteViewModel,
  val originalLocation: DiffLineLocation,
  discussionsViewOption: StateFlow<DiscussionsViewOption>
) : NewGitLabNoteViewModel by base,
    DiffMapped {
  override val location: StateFlow<DiffLineLocation?> = MutableStateFlow(originalLocation)
  override val isVisible: StateFlow<Boolean> = discussionsViewOption.mapState { it != DiscussionsViewOption.DONT_SHOW }
}