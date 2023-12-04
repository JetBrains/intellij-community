// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.editor.EditorMapped
import com.intellij.diff.util.Side
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.mergerequest.data.mapToLocation
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel

interface GitLabMergeRequestEditorDiscussionViewModel : GitLabMergeRequestDiscussionViewModel, EditorMapped

internal class GitLabMergeRequestEditorDiscussionViewModelImpl(
  base: GitLabMergeRequestDiscussionViewModel,
  diffData: GitTextFilePatchWithHistory,
  discussionsViewOption: Flow<DiscussionsViewOption>
) : GitLabMergeRequestDiscussionViewModel by base,
    GitLabMergeRequestEditorDiscussionViewModel {

  override val line: Flow<Int?> = base.position.map {
    it?.mapToLocation(diffData, Side.RIGHT)?.takeIf { it.first == Side.RIGHT }?.second
  }

  override val isVisible: Flow<Boolean> = combine(resolveVm?.resolved ?: flowOf(false), discussionsViewOption) { isResolved, viewOption ->
    return@combine when (viewOption) {
      DiscussionsViewOption.ALL -> true
      DiscussionsViewOption.UNRESOLVED_ONLY -> !isResolved
      DiscussionsViewOption.DONT_SHOW -> false
    }
  }
}

interface GitLabMergeRequestEditorNewDiscussionViewModel : NewGitLabNoteViewModel, EditorMapped {
  val originalLine: Int
}

internal class GitLabMergeRequestEditorNewDiscussionViewModelImpl(
  base: NewGitLabNoteViewModel,
  override val originalLine: Int,
  discussionsViewOption: Flow<DiscussionsViewOption>
) : NewGitLabNoteViewModel by base,
    GitLabMergeRequestEditorNewDiscussionViewModel {

  override val line: Flow<Int?> = flowOf(originalLine)
  override val isVisible: Flow<Boolean> = discussionsViewOption.map { it != DiscussionsViewOption.DONT_SHOW }
}