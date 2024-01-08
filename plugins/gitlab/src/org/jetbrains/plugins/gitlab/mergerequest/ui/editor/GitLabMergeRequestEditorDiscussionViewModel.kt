// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.editor.EditorMapped
import com.intellij.diff.util.Side
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.gitlab.mergerequest.data.mapToLocation
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestStandaloneDraftNoteViewModelBase
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel

class GitLabMergeRequestEditorDiscussionViewModel internal constructor(
  base: GitLabMergeRequestDiscussionViewModel,
  diffData: GitTextFilePatchWithHistory,
  discussionsViewOption: StateFlow<DiscussionsViewOption>
) : GitLabMergeRequestDiscussionViewModel by base, EditorMapped {

  override val line: StateFlow<Int?> = base.position.mapState {
    it?.mapToLocation(diffData, Side.RIGHT)?.takeIf { it.first == Side.RIGHT }?.second
  }

  override val isVisible: StateFlow<Boolean> = isResolved.combineState(discussionsViewOption) { isResolved, viewOption ->
    return@combineState when (viewOption) {
      DiscussionsViewOption.ALL -> true
      DiscussionsViewOption.UNRESOLVED_ONLY -> !isResolved
      DiscussionsViewOption.DONT_SHOW -> false
    }
  }
}

class GitLabMergeRequestEditorDraftNoteViewModel internal constructor(
  base: GitLabMergeRequestStandaloneDraftNoteViewModelBase,
  diffData: GitTextFilePatchWithHistory,
  discussionsViewOption: StateFlow<DiscussionsViewOption>
) : GitLabNoteViewModel by base, EditorMapped {

  override val line: StateFlow<Int?> = base.position.mapState {
    it?.mapToLocation(diffData, Side.RIGHT)?.takeIf { it.first == Side.RIGHT }?.second
  }

  override val isVisible: StateFlow<Boolean> = discussionsViewOption.mapState {
    when (it) {
      DiscussionsViewOption.UNRESOLVED_ONLY, DiscussionsViewOption.ALL -> true
      DiscussionsViewOption.DONT_SHOW -> false
    }
  }
}

class GitLabMergeRequestEditorNewDiscussionViewModel internal constructor(
  base: NewGitLabNoteViewModel,
  val originalLine: Int,
  discussionsViewOption: StateFlow<DiscussionsViewOption>
) : NewGitLabNoteViewModel by base, EditorMapped {

  override val line: StateFlow<Int?> = MutableStateFlow(originalLine)
  override val isVisible: StateFlow<Boolean> = discussionsViewOption.mapState { it != DiscussionsViewOption.DONT_SHOW }
}