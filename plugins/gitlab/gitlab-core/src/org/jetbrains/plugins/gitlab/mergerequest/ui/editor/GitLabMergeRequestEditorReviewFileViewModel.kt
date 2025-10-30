// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.async.transformConsecutiveSuccesses
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.UnifiedCodeReviewItemPosition
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil
import com.intellij.openapi.diff.impl.patch.withoutContext
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitTextFilePatchWithHistory
import git4idea.changes.createVcsChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.data.GitLabImageLoader
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestNewDiscussionPosition
import org.jetbrains.plugins.gitlab.mergerequest.data.mapToLocation
import org.jetbrains.plugins.gitlab.mergerequest.ui.filterInFile
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestDiscussionsViewModels
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.mapToLocation
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabMergeRequestDiscussionUtil
import org.jetbrains.plugins.gitlab.mergerequest.util.toLines

interface GitLabMergeRequestEditorReviewFileViewModel {
  val change: RefComparisonChange

  val headContent: StateFlow<ComputedResult<CharSequence>?>
  val changedRanges: List<Range>

  fun getBaseContent(lines: LineRange): String?

  val discussions: StateFlow<ComputedResult<Collection<GitLabMergeRequestEditorDiscussionViewModel>>>
  val draftNotes: StateFlow<ComputedResult<Collection<GitLabMergeRequestEditorDraftNoteViewModel>>>
  val linesWithDiscussions: StateFlow<Set<Int>>
  val linesWithNewDiscussions: StateFlow<Set<Int>>

  val canNavigate: Boolean

  val canComment: StateFlow<Boolean>
  val newDiscussions: StateFlow<Collection<GitLabMergeRequestEditorNewDiscussionViewModel>>

  val avatarIconsProvider: IconsProvider<GitLabUserDTO>
  val imageLoader: GitLabImageLoader

  fun lookupNextComment(line: Int, additionalIsVisible: (String) -> Boolean): String?
  fun lookupNextComment(noteTrackingId: String, additionalIsVisible: (String) -> Boolean): String?
  fun lookupPreviousComment(line: Int, additionalIsVisible: (String) -> Boolean): String?
  fun lookupPreviousComment(noteTrackingId: String, additionalIsVisible: (String) -> Boolean): String?

  fun getThreadPosition(noteTrackingId: String): Pair<RefComparisonChange, Int>?
  fun requestThreadFocus(noteTrackingId: String)

  fun requestNewDiscussion(line: Int, focus: Boolean)
  fun cancelNewDiscussion(line: Int)

  fun showDiff(line: Int?)
}

internal class GitLabMergeRequestEditorReviewFileViewModelImpl(
  parentCs: CoroutineScope,
  private val project: Project,
  mergeRequest: GitLabMergeRequest,
  override val change: RefComparisonChange,
  private val diffData: GitTextFilePatchWithHistory,
  private val discussionsContainer: GitLabMergeRequestDiscussionsViewModels,
  private val reviewVm: GitLabMergeRequestEditorReviewViewModel,
  discussionsViewOption: StateFlow<DiscussionsViewOption>,
  override val avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  override val imageLoader: GitLabImageLoader,
) : GitLabMergeRequestEditorReviewFileViewModel {
  private val cs = parentCs.childScope(javaClass.name, Dispatchers.Default)

  override val headContent: StateFlow<ComputedResult<String>?> = flow {
    ComputedResult.compute {
      coroutineToIndicator {
        change.createVcsChange(project).afterRevision?.content ?: ""
      }.let {
        StringUtil.convertLineSeparators(it)
      }
    }.let {
      emit(it)
    }
  }.flowOn(Dispatchers.IO)
    .stateIn(cs, SharingStarted.Lazily, ComputedResult.loading())

  override val changedRanges: List<Range> = diffData.patch.hunks.withoutContext().toList()

  override fun getBaseContent(lines: LineRange): String? {
    if (lines.start == lines.end) return ""
    return PatchHunkUtil.getLinesLeft(diffData.patch, lines)
  }

  override val discussions: StateFlow<ComputedResult<Collection<GitLabMergeRequestEditorDiscussionViewModel>>> =
    reviewVm.discussions
      .transformConsecutiveSuccesses { filterInFile(change) }
      .stateInNow(cs, ComputedResult.loading())
  override val draftNotes: StateFlow<ComputedResult<Collection<GitLabMergeRequestEditorDraftNoteViewModel>>> =
    reviewVm.draftNotes
      .transformConsecutiveSuccesses { filterInFile(change) }
      .stateInNow(cs, ComputedResult.loading())
  override val newDiscussions: StateFlow<Collection<GitLabMergeRequestEditorNewDiscussionViewModel>> =
    discussionsContainer.newDiscussions.map {
      it.mapNotNull { (position, vm) ->
        val line = position.mapToLocation(diffData)?.takeIf { it.first == Side.RIGHT }?.second ?: return@mapNotNull null
        GitLabMergeRequestEditorNewDiscussionViewModel(vm, line, discussionsViewOption)
      }
    }.stateInNow(cs, emptyList())

  override val linesWithDiscussions: StateFlow<Set<Int>> =
    GitLabMergeRequestDiscussionUtil
      .createDiscussionsPositionsFlow(mergeRequest, discussionsViewOption).toLines {
        it.mapToLocation(diffData, Side.RIGHT)?.takeIf { it.first == Side.RIGHT }?.second
      }.stateInNow(cs, emptySet())

  override val canNavigate: Boolean = diffData.isCumulative

  override val canComment: StateFlow<Boolean> = discussionsViewOption.mapState { it != DiscussionsViewOption.DONT_SHOW }

  @OptIn(ExperimentalCoroutinesApi::class)
  override val linesWithNewDiscussions: StateFlow<Set<Int>> =
    discussionsContainer.newDiscussions
      .map {
        it.keys.mapNotNullTo(mutableSetOf()) {
          it.mapToLocation(diffData)?.takeIf { it.first == Side.RIGHT }?.second ?: return@mapNotNullTo null
        }
      }
      .stateInNow(cs, emptySet())

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

  override fun lookupNextComment(line: Int, additionalIsVisible: (String) -> Boolean): String? =
    reviewVm.lookupNextComment(lineToUnified(line), additionalIsVisible)

  override fun lookupNextComment(noteTrackingId: String, additionalIsVisible: (String) -> Boolean): String? =
    reviewVm.lookupNextComment(noteTrackingId, additionalIsVisible)

  override fun lookupPreviousComment(line: Int, additionalIsVisible: (String) -> Boolean): String? =
    reviewVm.lookupPreviousComment(lineToUnified(line), additionalIsVisible)

  override fun lookupPreviousComment(noteTrackingId: String, additionalIsVisible: (String) -> Boolean): String? =
    reviewVm.lookupPreviousComment(noteTrackingId, additionalIsVisible)

  override fun getThreadPosition(noteTrackingId: String): Pair<RefComparisonChange, Int>? =
    reviewVm.lookupThreadPosition(noteTrackingId)

  override fun requestThreadFocus(noteTrackingId: String) {
    reviewVm.requestThreadFocus(noteTrackingId)
  }

  /**
   * We don't really care about the left-sided line number. It needs to be at the beginning to make sure
   * the first comment on the line is picked though.
   */
  private fun lineToUnified(line: Int): UnifiedCodeReviewItemPosition =
    UnifiedCodeReviewItemPosition(change, leftLine = -1, rightLine = line)

  private val _showDiffRequests = MutableSharedFlow<Int?>(extraBufferCapacity = 1)
  val showDiffRequests: SharedFlow<Int?> = _showDiffRequests.asSharedFlow()

  override fun showDiff(line: Int?) {
    _showDiffRequests.tryEmit(line)
  }
}