// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.combineStates
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.FocusableViewModel
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.editor.CodeReviewInlayModel
import com.intellij.diff.util.Side
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestNewDiscussionPosition
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNoteLocation
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabNotePosition
import org.jetbrains.plugins.gitlab.mergerequest.data.mapToLocation
import org.jetbrains.plugins.gitlab.mergerequest.ui.DiffDataMappedGitLabMergeRequestInlayModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.mapToLocation
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestStandaloneDraftNoteViewModelBase
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModelWithAdjustablePosition
import java.util.*

@ApiStatus.Internal
interface DiffDataMappedGitLabMergeRequestEditorViewModel
  : CodeReviewInlayModel,
    DiffDataMappedGitLabMergeRequestInlayModel,
    FocusableViewModel {
  val location: StateFlow<GitLabNoteLocation?>
}

@ApiStatus.Internal
class GitLabMergeRequestEditorDiscussionViewModel(
  base: GitLabMergeRequestDiscussionViewModel,
  override val diffData: StateFlow<DiffDataMappedGitLabMergeRequestInlayModel.DiffData?>,
  discussionsViewOption: StateFlow<DiscussionsViewOption>,
) : GitLabMergeRequestDiscussionViewModel by base, DiffDataMappedGitLabMergeRequestEditorViewModel {
  override val key: Any = base.id
  override val location: StateFlow<GitLabNoteLocation?> = mapPositionToRightLocation(base.position, diffData)
  override val line: StateFlow<Int?> = location.mapState { it?.lineIdx }

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
  override val location: StateFlow<GitLabNoteLocation?> = mapPositionToRightLocation(base.position, diffData)
  override val line: StateFlow<Int?> = location.mapState { it?.lineIdx }

  override val isVisible: StateFlow<Boolean> = discussionsViewOption.mapState {
    when (it) {
      DiscussionsViewOption.UNRESOLVED_ONLY, DiscussionsViewOption.ALL -> true
      DiscussionsViewOption.DONT_SHOW -> false
    }
  }
}

@ApiStatus.Internal
class GitLabMergeRequestEditorNewDiscussionViewModel internal constructor(
  private val base: NewGitLabNoteViewModelWithAdjustablePosition,
  private val diffData: GitTextFilePatchWithHistory,
  discussionsViewOption: StateFlow<DiscussionsViewOption>,
) : NewGitLabNoteViewModelWithAdjustablePosition by base, CodeReviewInlayModel {
  val location: StateFlow<GitLabNoteLocation?> = position.mapState { it.mapToLocation(diffData) }
  override val key: Any = "NEW_${UUID.randomUUID()}"
  override val line: StateFlow<Int?> = location.mapState { it?.lineIdx }
  override val isVisible: StateFlow<Boolean> = discussionsViewOption.mapState { it != DiscussionsViewOption.DONT_SHOW }
  fun updateLineRange(startLocation: DiffLineLocation?, endLocation: DiffLineLocation?) {
    val oldLocation = location.value ?: return
    val newLocation = GitLabNoteLocation(startLocation?.first ?: oldLocation.startSide,
                                         startLocation?.second ?: oldLocation.startLineIdx,
                                         endLocation?.first ?: oldLocation.side,
                                         endLocation?.second ?: oldLocation.lineIdx)
    val newPosition = GitLabMergeRequestDiscussionsViewModels.NewDiscussionPosition(
      GitLabMergeRequestNewDiscussionPosition.calcFor(diffData, newLocation), Side.RIGHT
    )
    updatePosition(newPosition)
  }
}

private fun mapPositionToRightLocation(
  position: GitLabNotePosition?,
  diffData: StateFlow<DiffDataMappedGitLabMergeRequestInlayModel.DiffData?>,
): StateFlow<GitLabNoteLocation?> =
  diffData.mapState { diffDataOrNull ->
    val diffData = diffDataOrNull?.diffData ?: return@mapState null

    position?.mapToLocation(diffData, Side.RIGHT)
      ?.takeIf { it.startSide == Side.RIGHT && it.side == Side.RIGHT }
  }

private fun mapPositionToRightLocation(
  position: StateFlow<GitLabNotePosition?>,
  diffData: StateFlow<DiffDataMappedGitLabMergeRequestInlayModel.DiffData?>,
): StateFlow<GitLabNoteLocation?> =
  combineStates(position, diffData) { positionOrNull, diffDataOrNull ->
    val position = positionOrNull ?: return@combineStates null
    val diffData = diffDataOrNull?.diffData ?: return@combineStates null

    position.mapToLocation(diffData, Side.RIGHT)
      ?.takeIf { it.startSide == Side.RIGHT && it.side == Side.RIGHT }
  }
