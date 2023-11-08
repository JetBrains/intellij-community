// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.withoutContext
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import git4idea.changes.GitTextFilePatchWithHistory
import git4idea.changes.createVcsChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestNewDiscussionPosition
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.mapToLocation

interface GitLabMergeRequestEditorReviewFileViewModel {
  val changedRanges: List<Range>

  val discussions: Flow<Collection<GitLabMergeRequestEditorDiscussionViewModel>>
  val draftDiscussions: Flow<Collection<GitLabMergeRequestEditorDiscussionViewModel>>

  val canComment: Flow<Boolean>
  val newDiscussions: Flow<Collection<GitLabMergeRequestEditorNewDiscussionViewModel>>

  val avatarIconsProvider: IconsProvider<GitLabUserDTO>

  suspend fun getOriginalContent(): CharSequence
  fun getOriginalContent(range: LineRange): String?

  fun requestNewDiscussion(line: Int, focus: Boolean)
  fun cancelNewDiscussion(line: Int)

  fun showDiff(line: Int?)
}

internal class GitLabMergeRequestEditorReviewFileViewModelImpl(
  private val project: Project,
  private val change: RefComparisonChange,
  private val diffData: GitTextFilePatchWithHistory,
  private val discussionsContainer: GitLabMergeRequestDiscussionsViewModels,
  discussionsViewOption: Flow<DiscussionsViewOption>,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>
) : GitLabMergeRequestEditorReviewFileViewModel {

  override val changedRanges: List<Range> = diffData.patch.hunks.withoutContext().toList()

  override val discussions: Flow<Collection<GitLabMergeRequestEditorDiscussionViewModel>> =
    discussionsContainer.discussions.map {
      it.map { GitLabMergeRequestEditorDiscussionViewModelImpl(it, diffData, discussionsViewOption) }
    }
  override val draftDiscussions: Flow<Collection<GitLabMergeRequestEditorDiscussionViewModel>> =
    discussionsContainer.draftDiscussions.map {
      it.map { GitLabMergeRequestEditorDiscussionViewModelImpl(it, diffData, discussionsViewOption) }
    }

  override val canComment: Flow<Boolean> = discussionsViewOption.map { it != DiscussionsViewOption.DONT_SHOW }
  override val newDiscussions: Flow<Collection<GitLabMergeRequestEditorNewDiscussionViewModel>> = discussionsContainer.newDiscussions.map {
    it.mapNotNull { (position, vm) ->
      val line = position.mapToLocation(diffData)?.takeIf { it.first == Side.RIGHT }?.second ?: return@mapNotNull null
      GitLabMergeRequestEditorNewDiscussionViewModelImpl(vm, line, discussionsViewOption)
    }
  }

  override suspend fun getOriginalContent(): String = withContext(Dispatchers.IO) {
    coroutineToIndicator {
      change.createVcsChange(project).afterRevision?.content ?: ""
    }.let {
      StringUtil.convertLineSeparators(it)
    }
  }

  override fun getOriginalContent(range: LineRange): String? {
    if (range.start == range.end) return ""
    return diffData.patch.hunks.find {
      it.startLineBefore <= range.start && it.endLineBefore >= range.end
    }?.let { hunk ->
      val builder = StringBuilder()
      var lineCounter = hunk.startLineBefore
      for (line in hunk.lines) {
        if (line.type == PatchLine.Type.CONTEXT) {
          lineCounter++
        }
        if (line.type == PatchLine.Type.REMOVE) {
          if (lineCounter >= range.start) {
            builder.append(line.text)
            if (!line.isSuppressNewLine) {
              builder.append("\n")
            }
          }
          lineCounter++
        }
        if (lineCounter >= range.end) break
      }
      return builder.toString()
    }
  }

  override fun requestNewDiscussion(line: Int, focus: Boolean) {
    val position = GitLabMergeRequestNewDiscussionPosition.calcFor(diffData, DiffLineLocation(Side.RIGHT, line)).let {
      GitLabMergeRequestDiscussionsViewModels.NewDiscussionPosition(it, Side.RIGHT)
    }
    discussionsContainer.requestNewDiscussion(position, focus)
  }

  override fun cancelNewDiscussion(line: Int) {
    val position = GitLabMergeRequestNewDiscussionPosition.calcFor(diffData, DiffLineLocation(Side.RIGHT, line)).let {
      GitLabMergeRequestDiscussionsViewModels.NewDiscussionPosition(it, Side.RIGHT)
    }
    discussionsContainer.cancelNewDiscussion(position)
  }

  private val _showDiffRequests = MutableSharedFlow<Int?>(extraBufferCapacity = 1)
  val showDiffRequests: SharedFlow<Int?> = _showDiffRequests.asSharedFlow()

  override fun showDiff(line: Int?) {
    _showDiffRequests.tryEmit(line)
  }
}