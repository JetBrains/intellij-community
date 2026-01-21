// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.diff

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.combineStates
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.FocusableViewModel
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.diff.util.Side
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNoteLocation
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNotePosition
import org.jetbrains.plugins.gitlab.mergerequest.data.mapToLocation
import org.jetbrains.plugins.gitlab.mergerequest.ui.DiffDataMappedGitLabMergeRequestInlayModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestStandaloneDraftNoteViewModelBase
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel

interface DiffDataMappedGitLabMergeRequestDiffInlayViewModel
  : FocusableViewModel,
    DiffDataMappedGitLabMergeRequestInlayModel {
  val location: StateFlow<GitLabNoteLocation?>
  val isVisible: StateFlow<Boolean>
}

class GitLabMergeRequestDiffDiscussionViewModel internal constructor(
  base: GitLabMergeRequestDiscussionViewModel,
  override val diffData: StateFlow<DiffDataMappedGitLabMergeRequestInlayModel.DiffData?>,
  discussionsViewOption: StateFlow<DiscussionsViewOption>,
) : GitLabMergeRequestDiscussionViewModel by base, DiffDataMappedGitLabMergeRequestDiffInlayViewModel {
  override val location: StateFlow<GitLabNoteLocation?> = mapPositionToDiffLine(base.position, diffData)

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
  override val diffData: StateFlow<DiffDataMappedGitLabMergeRequestInlayModel.DiffData?>,
  discussionsViewOption: StateFlow<DiscussionsViewOption>,
) : GitLabNoteViewModel by base, DiffDataMappedGitLabMergeRequestDiffInlayViewModel {
  override val location: StateFlow<GitLabNoteLocation?> = mapPositionToDiffLine(base.position, diffData)

  override val isVisible: StateFlow<Boolean> = discussionsViewOption.mapState {
    when (it) {
      DiscussionsViewOption.UNRESOLVED_ONLY, DiscussionsViewOption.ALL -> true
      DiscussionsViewOption.DONT_SHOW -> false
    }
  }
}

class GitLabMergeRequestDiffNewDiscussionViewModel internal constructor(
  base: NewGitLabNoteViewModel,
  originalLocation: GitLabNoteLocation,
  discussionsViewOption: StateFlow<DiscussionsViewOption>,
) : NewGitLabNoteViewModel by base {
  val location: StateFlow<GitLabNoteLocation?> = MutableStateFlow(originalLocation)
  val isVisible: StateFlow<Boolean> = discussionsViewOption.mapState { it != DiscussionsViewOption.DONT_SHOW }
}

private fun mapPositionToDiffLine(
  position: StateFlow<GitLabNotePosition?>,
  diffData: StateFlow<DiffDataMappedGitLabMergeRequestInlayModel.DiffData?>,
): StateFlow<GitLabNoteLocation?> =
  combineStates(position, diffData) { positionOrNull, diffDataOrNull ->
    val position = positionOrNull ?: return@combineStates null
    val diffData = diffDataOrNull?.diffData ?: return@combineStates null

    position.mapToLocation(diffData, Side.LEFT)
  }
