// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.classAsCoroutineName
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.withoutContext
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitTextFilePatchWithHistory
import git4idea.changes.createVcsChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestNewDiscussionPosition
import org.jetbrains.plugins.gitlab.mergerequest.data.mapToLocation
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.mapToLocation
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabMergeRequestEditorCommentsUtil
import org.jetbrains.plugins.gitlab.mergerequest.util.toLines

interface GitLabMergeRequestEditorReviewFileViewModel {
  val originalContent: StateFlow<ComputedResult<CharSequence>?>
  val changedRanges: List<Range>

  val discussions: StateFlow<Collection<GitLabMergeRequestEditorDiscussionViewModel>>
  val draftNotes: StateFlow<Collection<GitLabMergeRequestEditorDraftNoteViewModel>>
  val linesWithDiscussions: StateFlow<Set<Int>>

  val canComment: StateFlow<Boolean>
  val newDiscussions: StateFlow<Collection<GitLabMergeRequestEditorNewDiscussionViewModel>>

  val avatarIconsProvider: IconsProvider<GitLabUserDTO>

  fun getOriginalContent(range: LineRange): String?

  fun requestNewDiscussion(line: Int, focus: Boolean)
  fun cancelNewDiscussion(line: Int)

  fun showDiff(line: Int?)
}

internal class GitLabMergeRequestEditorReviewFileViewModelImpl(
  parentCs: CoroutineScope,
  private val project: Project,
  mergeRequest: GitLabMergeRequest,
  private val change: RefComparisonChange,
  private val diffData: GitTextFilePatchWithHistory,
  private val discussionsContainer: GitLabMergeRequestDiscussionsViewModels,
  discussionsViewOption: StateFlow<DiscussionsViewOption>,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>
) : GitLabMergeRequestEditorReviewFileViewModel {
  private val cs = parentCs.childScope(classAsCoroutineName())

  override val originalContent: StateFlow<ComputedResult<String>?> = flow {
    ComputedResult.compute {
      coroutineToIndicator {
        change.createVcsChange(project).afterRevision?.content ?: ""
      }.let {
        StringUtil.convertLineSeparators(it)
      }
    }.let {
      emit(it)
    }
  }.stateIn(cs, SharingStarted.Lazily, ComputedResult.loading())

  override val changedRanges: List<Range> = diffData.patch.hunks.withoutContext().toList()

  override val discussions: StateFlow<Collection<GitLabMergeRequestEditorDiscussionViewModel>> =
    discussionsContainer.discussions.map {
      it.map { GitLabMergeRequestEditorDiscussionViewModel(it, diffData, discussionsViewOption) }
    }.stateInNow(cs, emptyList())
  override val draftNotes: StateFlow<Collection<GitLabMergeRequestEditorDraftNoteViewModel>> =
    discussionsContainer.draftNotes.map {
      it.map { GitLabMergeRequestEditorDraftNoteViewModel(it, diffData, discussionsViewOption) }
    }.stateInNow(cs, emptyList())
  override val linesWithDiscussions: StateFlow<Set<Int>> =
    GitLabMergeRequestEditorCommentsUtil
      .createDiscussionsPositionsFlow(mergeRequest, discussionsViewOption).toLines {
        it.mapToLocation(diffData, Side.RIGHT)?.takeIf { it.first == Side.RIGHT }?.second
      }.stateInNow(cs, emptySet())

  override val canComment: StateFlow<Boolean> = discussionsViewOption.mapState { it != DiscussionsViewOption.DONT_SHOW }
  override val newDiscussions: StateFlow<Collection<GitLabMergeRequestEditorNewDiscussionViewModel>> =
    discussionsContainer.newDiscussions.map {
      it.mapNotNull { (position, vm) ->
        val line = position.mapToLocation(diffData)?.takeIf { it.first == Side.RIGHT }?.second ?: return@mapNotNull null
        GitLabMergeRequestEditorNewDiscussionViewModel(vm, line, discussionsViewOption)
      }
    }.stateInNow(cs, emptyList())

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