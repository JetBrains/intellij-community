// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.*
import com.intellij.collaboration.ui.codereview.editor.*
import com.intellij.collaboration.util.*
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.openapi.Disposable
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import org.jetbrains.plugins.gitlab.mergerequest.GitLabMergeRequestsPreferences

/**
 * A wrapper over [GitLabMergeRequestEditorReviewFileViewModel] to encapsulate LST integration
 */
internal class GitLabMergeRequestEditorReviewUIModel internal constructor(
  private val cs: CoroutineScope,
  private val preferences: GitLabMergeRequestsPreferences,
  private val fileVm: GitLabMergeRequestEditorReviewFileViewModel,
  private val changesModel: MutableCodeReviewEditorGutterChangesModel = MutableCodeReviewEditorGutterChangesModel(),
  @RequiresEdt private val showEditor: (RefComparisonChange, Int) -> Unit,
) : CodeReviewEditorGutterChangesModel by changesModel,
    CodeReviewEditorGutterActionableChangesModel,
    CodeReviewEditorInlaysModel<GitLabMergeRequestEditorMappedComponentModel>,
    CodeReviewEditorGutterControlsModel,
    CodeReviewNavigableEditorViewModel {

  private val postReviewRanges = MutableStateFlow<List<Range>?>(null)

  override var shouldHighlightDiffRanges: Boolean
    get() = preferences.highlightDiffLinesInEditor
    set(value) {
      preferences.highlightDiffLinesInEditor = value
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  override val gutterControlsState: StateFlow<CodeReviewEditorGutterControlsModel.ControlsState?> =
    combine(
      postReviewRanges,
      fileVm.canComment,
      fileVm.linesWithDiscussions,
      fileVm.linesWithNewDiscussions
    ) { postReviewRanges, canComment, linesWithDiscussions, linesWithNewDiscussions ->
      if (postReviewRanges != null) {
        val nonCommentableRanges = postReviewRanges.map(Range::getAfterLines)
        val shiftedLinesWithDiscussions = linesWithDiscussions.mapTo(mutableSetOf()) {
          ReviewInEditorUtil.transferLineToAfter(postReviewRanges, it)
        }
        val shiftedLinesWithNewDiscussions = linesWithNewDiscussions.mapTo(mutableSetOf()) {
          ReviewInEditorUtil.transferLineToAfter(postReviewRanges, it)
        }
        GutterState(shiftedLinesWithDiscussions, shiftedLinesWithNewDiscussions, canComment, nonCommentableRanges)
      }
      else null
    }.stateInNow(cs, null)

  private val discussions = fileVm.discussions
    .transformConsecutiveSuccesses { mapStatefulToStateful { ShiftedDiscussion(it) } }
    .stateInNow(cs, ComputedResult.loading())
  private val drafts = fileVm.draftNotes
    .transformConsecutiveSuccesses { mapStatefulToStateful { ShiftedDraftNote(it) } }
    .stateInNow(cs, ComputedResult.loading())
  private val newDiscussions = fileVm.newDiscussions
    .mapStatefulToStateful { ShiftedNewDiscussion(it) }
    .stateInNow(cs, emptyList())

  override val inlays: StateFlow<Collection<GitLabMergeRequestEditorMappedComponentModel>> = combine(discussions, drafts, newDiscussions) { discussionsResult, draftsResult, new ->
    (discussionsResult.getOrNull() ?: emptyList()) + (draftsResult.getOrNull() ?: emptyList()) + new
  }.stateInNow(cs, emptyList())

  override fun requestNewComment(lineIdx: Int) {
    val ranges = postReviewRanges.value ?: return
    val originalLine = ReviewInEditorUtil.transferLineFromAfter(ranges, lineIdx)?.takeIf { it >= 0 } ?: return
    fileVm.requestNewDiscussion(originalLine, true)
  }

  override fun cancelNewComment(lineIdx: Int) {
    val ranges = postReviewRanges.value ?: return
    val originalLine = ReviewInEditorUtil.transferLineFromAfter(ranges, lineIdx)?.takeIf { it >= 0 } ?: return
    fileVm.cancelNewDiscussion(originalLine)
  }

  override fun toggleComments(lineIdx: Int) {
    inlays.value.asSequence().filter { it.line.value == lineIdx }.filterIsInstance<Hideable>().syncOrToggleAll()
  }

  fun cancelNewDiscussion(originalLine: Int) {
    fileVm.cancelNewDiscussion(originalLine)
  }

  override fun getBaseContent(lines: LineRange): String? = fileVm.getBaseContent(lines)

  override fun showDiff(lineIdx: Int?) {
    val ranges = postReviewRanges.value ?: return
    val originalLine = lineIdx?.let { ReviewInEditorUtil.transferLineFromAfter(ranges, it, true) }?.takeIf { it >= 0 }
    fileVm.showDiff(originalLine)
  }

  override fun addDiffHighlightListener(disposable: Disposable, listener: () -> Unit) {
    cs.launchNow {
      preferences.highlightDiffLinesInEditorState.collect {
        listener()
      }
    }.cancelOnDispose(disposable)
  }

  fun setPostReviewChanges(changedRanges: List<Range>) {
    postReviewRanges.value = changedRanges
    changesModel.setChanges(ExcludingApproximateChangedRangesShifter.shift(fileVm.changedRanges, changedRanges).map(Range::asLst))
  }

  override val canNavigate: Boolean get() = fileVm.canNavigate

  override fun canGotoNextComment(threadId: String): Boolean = fileVm.lookupNextComment(threadId, ::additionalIsVisible) != null
  override fun canGotoNextComment(line: Int): Boolean = fileVm.lookupNextComment(line.shiftLineFromAfterApproximate(), ::additionalIsVisible) != null
  override fun canGotoPreviousComment(threadId: String): Boolean = fileVm.lookupPreviousComment(threadId, ::additionalIsVisible) != null
  override fun canGotoPreviousComment(line: Int): Boolean = fileVm.lookupPreviousComment(line.shiftLineFromAfterApproximate(), ::additionalIsVisible) != null

  override fun gotoNextComment(threadId: String) {
    val noteId = fileVm.lookupNextComment(threadId, ::additionalIsVisible) ?: return
    gotoComment(noteId)
  }

  override fun gotoNextComment(line: Int) {
    val noteId = fileVm.lookupNextComment(line.shiftLineFromAfterApproximate(), ::additionalIsVisible) ?: return
    gotoComment(noteId)
  }

  override fun gotoPreviousComment(threadId: String) {
    val noteId = fileVm.lookupPreviousComment(threadId, ::additionalIsVisible) ?: return
    gotoComment(noteId)
  }

  override fun gotoPreviousComment(line: Int) {
    val noteId = fileVm.lookupPreviousComment(line.shiftLineFromAfterApproximate(), ::additionalIsVisible) ?: return
    gotoComment(noteId)
  }

  @RequiresEdt
  private fun gotoComment(threadId: String) {
    val (change, unmappedLine) = fileVm.getThreadPosition(threadId) ?: return

    val lineOrNull = if (change == fileVm.change) {
      // Only shift the line if it comes from this file
      unmappedLine.shiftLineToAfter()
      // if the line number is from a different file, we can't currently easily access outside changes to shift with
      // the current line would be a best-guess estimate
    }
    else unmappedLine
    val line = lineOrNull ?: return

    showEditor(change, line)
    fileVm.requestThreadFocus(threadId)
  }

  private fun additionalIsVisible(noteTrackingId: String): Boolean {
    val inlay = inlays.value.find { it.vm.trackingId == noteTrackingId } ?: return true // it's not hidden
    return inlay.isVisible.value
  }

  private fun StateFlow<Int?>.shiftLine(): StateFlow<Int?> =
    combineState(postReviewRanges) { line, ranges ->
      if (ranges != null && line != null) {
        ReviewInEditorUtil.transferLineToAfter(ranges, line).takeIf { it >= 0 }
      }
      else null
    }

  private fun Int.shiftLineToAfter(): Int? {
    val ranges = postReviewRanges.value ?: return null
    return ReviewInEditorUtil.transferLineToAfter(ranges, this)
  }

  private fun Int.shiftLineFromAfterApproximate(): Int {
    val ranges = postReviewRanges.value ?: return this
    return ReviewInEditorUtil.transferLineFromAfter(ranges, this, approximate = true)!!
  }

  private inner class ShiftedDiscussion(vm: GitLabMergeRequestEditorDiscussionViewModel)
    : GitLabMergeRequestEditorMappedComponentModel.Discussion<GitLabMergeRequestEditorDiscussionViewModel>(vm) {
    override val isVisible: StateFlow<Boolean> = vm.isVisible.combineState(hiddenState) { visible, hidden -> visible && !hidden }
    override val line: StateFlow<Int?> = vm.line.shiftLine()
  }

  private inner class ShiftedDraftNote(vm: GitLabMergeRequestEditorDraftNoteViewModel)
    : GitLabMergeRequestEditorMappedComponentModel.DraftNote<GitLabMergeRequestEditorDraftNoteViewModel>(vm) {
    override val isVisible: StateFlow<Boolean> = vm.isVisible.combineState(hiddenState) { visible, hidden -> visible && !hidden }
    override val line: StateFlow<Int?> = vm.line.shiftLine()
  }

  private inner class ShiftedNewDiscussion(vm: GitLabMergeRequestEditorNewDiscussionViewModel)
    : GitLabMergeRequestEditorMappedComponentModel.NewDiscussion<GitLabMergeRequestEditorNewDiscussionViewModel>(vm) {
    override val key: Any = vm.key
    override val isVisible: StateFlow<Boolean> = MutableStateFlow(true)
    override val line: StateFlow<Int?> = vm.line.shiftLine()

    override fun cancel() {
      vm.line.value?.let(::cancelNewDiscussion)
    }
  }
}

private data class GutterState(
  override val linesWithComments: Set<Int>,
  override val linesWithNewComments: Set<Int>,
  val canComment: Boolean,
  val nonCommentableRanges: List<LineRange>,
) : CodeReviewEditorGutterControlsModel.ControlsState {
  override fun isLineCommentable(lineIdx: Int): Boolean =
    canComment && nonCommentableRanges.none { lineIdx in it.start until it.end }
}

private fun Range.getAfterLines(): LineRange = LineRange(start2, end2)