// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.combineStates
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.FocusableViewModel
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.editor.CodeReviewInlayModel
import com.intellij.diff.util.Side
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNotePosition
import org.jetbrains.plugins.gitlab.mergerequest.data.mapToLocation
import org.jetbrains.plugins.gitlab.mergerequest.ui.DiffDataMappedGitLabMergeRequestInlayModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestStandaloneDraftNoteViewModelBase
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel

@ApiStatus.Internal
interface DiffDataMappedGitLabMergeRequestEditorViewModel
  : CodeReviewInlayModel,
    DiffDataMappedGitLabMergeRequestInlayModel,
    FocusableViewModel

@ApiStatus.Internal
class GitLabMergeRequestEditorDiscussionViewModel(
  base: GitLabMergeRequestDiscussionViewModel,
  override val diffData: StateFlow<DiffDataMappedGitLabMergeRequestInlayModel.DiffData?>,
  discussionsViewOption: StateFlow<DiscussionsViewOption>,
) : GitLabMergeRequestDiscussionViewModel by base, DiffDataMappedGitLabMergeRequestEditorViewModel {
  override val key: Any = base.id
  override val line: StateFlow<Int?> = mapPositionToRightLine(base.position, diffData)

  override val isVisible: StateFlow<Boolean> = isResolved.combineState(discussionsViewOption) { isResolved, viewOption ->
    return@combineState when (viewOption) {
      DiscussionsViewOption.ALL -> true
      DiscussionsViewOption.UNRESOLVED_ONLY -> !isResolved
      DiscussionsViewOption.DONT_SHOW -> false
    }
  }
}

@ApiStatus.Internal
class GitLabMergeRequestEditorDraftNoteViewModel internal constructor(
  base: GitLabMergeRequestStandaloneDraftNoteViewModelBase,
  override val diffData: StateFlow<DiffDataMappedGitLabMergeRequestInlayModel.DiffData?>,
  discussionsViewOption: StateFlow<DiscussionsViewOption>,
) : GitLabNoteViewModel by base, DiffDataMappedGitLabMergeRequestEditorViewModel {
  override val key: Any = base.id
  override val line: StateFlow<Int?> = mapPositionToRightLine(base.position, diffData)

  override val isVisible: StateFlow<Boolean> = discussionsViewOption.mapState {
    when (it) {
      DiscussionsViewOption.UNRESOLVED_ONLY, DiscussionsViewOption.ALL -> true
      DiscussionsViewOption.DONT_SHOW -> false
    }
  }
}

@ApiStatus.Internal
class GitLabMergeRequestEditorNewDiscussionViewModel internal constructor(
  base: NewGitLabNoteViewModel,
  originalLine: Int,
  discussionsViewOption: StateFlow<DiscussionsViewOption>,
) : NewGitLabNoteViewModel by base, CodeReviewInlayModel {
  override val key: Any = "NEW_${originalLine}"
  override val line: StateFlow<Int?> = MutableStateFlow(originalLine)
  override val isVisible: StateFlow<Boolean> = discussionsViewOption.mapState { it != DiscussionsViewOption.DONT_SHOW }
}

private fun mapPositionToRightLine(
  position: GitLabNotePosition?,
  diffData: StateFlow<DiffDataMappedGitLabMergeRequestInlayModel.DiffData?>,
): StateFlow<Int?> =
  diffData.mapState { diffDataOrNull ->
    val diffData = diffDataOrNull?.diffData ?: return@mapState null

    position?.mapToLocation(diffData, Side.RIGHT)
      ?.takeIf { it.first == Side.RIGHT }?.second
  }

private fun mapPositionToRightLine(
  position: StateFlow<GitLabNotePosition?>,
  diffData: StateFlow<DiffDataMappedGitLabMergeRequestInlayModel.DiffData?>,
): StateFlow<Int?> =
  combineStates(position, diffData) { positionOrNull, diffDataOrNull ->
    val position = positionOrNull ?: return@combineStates null
    val diffData = diffDataOrNull?.diffData ?: return@combineStates null

    position.mapToLocation(diffData, Side.RIGHT)
      ?.takeIf { it.first == Side.RIGHT }?.second
  }
