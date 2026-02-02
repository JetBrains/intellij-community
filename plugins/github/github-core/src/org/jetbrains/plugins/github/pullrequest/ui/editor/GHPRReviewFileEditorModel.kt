// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.flatMapLatestEach
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.mapStatefulToStateful
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.ui.codereview.editor.CodeReviewCommentableEditorModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorGutterActionableChangesModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorGutterChangesModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorGutterControlsModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewNavigableEditorViewModel
import com.intellij.collaboration.ui.codereview.editor.MutableCodeReviewEditorGutterChangesModel
import com.intellij.collaboration.ui.codereview.editor.ReviewInEditorUtil
import com.intellij.collaboration.ui.codereview.editor.asLst
import com.intellij.collaboration.util.ExcludingApproximateChangedRangesShifter
import com.intellij.collaboration.util.Hideable
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.syncOrToggleAll
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation
import java.util.UUID

internal class GHPRReviewFileEditorModel internal constructor(
  private val cs: CoroutineScope,
  private val project: Project,
  private val settings: GithubPullRequestsProjectUISettings,
  private val fileVm: GHPRReviewFileEditorViewModel,
  private val changesModel: MutableCodeReviewEditorGutterChangesModel = MutableCodeReviewEditorGutterChangesModel(),
  @RequiresEdt private val showEditor: (RefComparisonChange, Int) -> Unit,
) : CodeReviewEditorGutterChangesModel by changesModel,
    CodeReviewEditorGutterActionableChangesModel,
    CodeReviewEditorModel<GHPREditorMappedComponentModel>,
    CodeReviewNavigableEditorViewModel,
    CodeReviewCommentableEditorModel.WithMultilineComments {

  private val postReviewRanges = MutableStateFlow<List<Range>?>(null)

  override var shouldHighlightDiffRanges: Boolean by settings::highlightDiffLinesInEditor

  override val canNavigate: Boolean get() = true

  @OptIn(ExperimentalCoroutinesApi::class)
  private val linesWithNewCommentsFlow: StateFlow<Set<Int>> =
    fileVm.newComments.flatMapLatestEach { vm ->
      vm.location.map { loc -> loc.lineIdx }
    }.map { it.toSet() }
      .stateInNow(cs, emptySet())

  override val gutterControlsState: StateFlow<CodeReviewEditorGutterControlsModel.ControlsState?> =
    combine(postReviewRanges, fileVm.linesWithComments, linesWithNewCommentsFlow) { postReviewRanges, linesWithComments, newCommentsLines ->
      if (postReviewRanges != null) {
        val shiftedLinesWithComments = linesWithComments.mapTo(mutableSetOf()) {
          ReviewInEditorUtil.transferLineToAfter(postReviewRanges, it)
        }
        val shiftedCommentableRanges = ExcludingApproximateChangedRangesShifter.shift(fileVm.commentableRanges, postReviewRanges).map {
          it.getAfterLines()
        }
        val linesWithNewComments = newCommentsLines.mapTo(mutableSetOf()) {
          ReviewInEditorUtil.transferLineToAfter(postReviewRanges, it)
        }
        GHPRReviewEditorGutterControlsState(shiftedLinesWithComments, linesWithNewComments, shiftedCommentableRanges)
      }
      else null
    }.stateInNow(cs, null)

  override val inlays: StateFlow<Collection<GHPREditorMappedComponentModel>> = combine(
    fileVm.threads.mapStatefulToStateful { ShiftedThread(it) },
    fileVm.newComments.mapStatefulToStateful { ShiftedNewComment(cs, it) },
  ) { threads, new ->
    // very explicit ordering: if we order back to front, loading of editor appears smoother (most initial loading happens off-screen)
    threads.sortedByDescending { it.line.value ?: -1 } + new
  }.stateInNow(cs, emptyList())

  override fun requestNewComment(lineIdx: Int) {
    val ranges = postReviewRanges.value ?: return
    val originalLine = ReviewInEditorUtil.transferLineFromAfter(ranges, lineIdx)?.takeIf { it >= 0 } ?: return
    fileVm.requestNewComment(originalLine, true)
  }

  override fun cancelNewComment(lineIdx: Int) {
    val ranges = postReviewRanges.value ?: return
    val loc = ReviewInEditorUtil.transferLineFromAfter(ranges, lineIdx)?.takeIf { it >= 0 } ?: return
    fileVm.cancelNewComment(loc)
  }

  override fun toggleComments(lineIdx: Int) {
    inlays.value.asSequence().filter { it.line.value == lineIdx }.filterIsInstance<Hideable>().syncOrToggleAll()
    GHPRStatisticsCollector.logToggledComments(project)
  }

  override fun getBaseContent(lines: LineRange): String? = fileVm.getBaseContent(lines)

  override fun showDiff(lineIdx: Int?) {
    val ranges = postReviewRanges.value ?: return
    val originalLine = lineIdx?.let { ReviewInEditorUtil.transferLineFromAfter(ranges, it, true) }?.takeIf { it >= 0 }
    fileVm.showDiff(originalLine)
  }

  override fun addDiffHighlightListener(disposable: Disposable, listener: () -> Unit) {
    cs.launch {
      settings.highlightDiffLinesInEditorState.collect {
        listener()
      }
    }.cancelOnDispose(disposable)
  }

  fun setPostReviewChanges(changedRanges: List<Range>) {
    postReviewRanges.value = changedRanges
    changesModel.setChanges(ExcludingApproximateChangedRangesShifter.shift(fileVm.changedRanges, changedRanges).map(Range::asLst))
  }

  override fun canGotoNextComment(threadId: String): Boolean = fileVm.lookupNextComment(threadId) != null
  override fun canGotoNextComment(line: Int): Boolean = fileVm.lookupNextComment(line.shiftLineToBefore()) != null
  override fun canGotoPreviousComment(threadId: String): Boolean = fileVm.lookupPreviousComment(threadId) != null
  override fun canGotoPreviousComment(line: Int): Boolean = fileVm.lookupPreviousComment(line.shiftLineToBefore()) != null

  @RequiresEdt
  override fun gotoNextComment(threadId: String) {
    val commentId = fileVm.lookupNextComment(threadId) ?: return
    gotoComment(commentId)
  }

  @RequiresEdt
  override fun gotoNextComment(line: Int) {
    val commentId = fileVm.lookupNextComment(line.shiftLineToBefore()) ?: return
    gotoComment(commentId)
  }

  @RequiresEdt
  override fun gotoPreviousComment(threadId: String) {
    val commentId = fileVm.lookupPreviousComment(threadId) ?: return
    gotoComment(commentId)
  }

  @RequiresEdt
  override fun gotoPreviousComment(line: Int) {
    val commentId = fileVm.lookupPreviousComment(line.shiftLineToBefore()) ?: return
    gotoComment(commentId)
  }

  @RequiresEdt
  private fun gotoComment(threadId: String) {
    val (change, unmappedLine) = fileVm.getThreadPosition(threadId) ?: return
    val line = if (change == fileVm.change) {
      // Only shift the line if it comes from this file
      unmappedLine.shiftLineToAfter()
      // if the line number is from a different file, we can't currently easily access outside changes to shift with
      // the current line would be a best-guess estimate
    }
    else unmappedLine

    showEditor(change, line)
    fileVm.requestThreadFocus(threadId)
  }

  private fun Int.shiftLineToAfter(): Int {
    val ranges = postReviewRanges.value ?: return this
    return ReviewInEditorUtil.transferLineToAfter(ranges, this)
  }

  private fun Int.shiftLineToBefore(): Int {
    val ranges = postReviewRanges.value ?: return this
    return ReviewInEditorUtil.transferLineFromAfter(ranges, this, approximate = true) ?: 0
  }

  override fun requestNewComment(lineRange: LineRange) {
    val ranges = postReviewRanges.value ?: return
    val originalStartLine = ReviewInEditorUtil.transferLineFromAfter(ranges, lineRange.start)?.takeIf { it >= 0 } ?: return
    val originalEndLine = ReviewInEditorUtil.transferLineFromAfter(ranges, lineRange.end)?.takeIf { it >= 0 } ?: return
    fileVm.requestNewComment(LineRange(originalStartLine, originalEndLine), true)
  }

  override fun canCreateComment(lineRange: LineRange): Boolean {
    val gutterControls = gutterControlsState.value ?: return false
    return gutterControls.isLineCommentable(lineRange.start) &&
           gutterControls.isLineCommentable(lineRange.end)
  }

  private inner class ShiftedThread(vm: GHPRReviewFileEditorThreadViewModel)
    : GHPREditorMappedComponentModel.Thread<GHPRReviewFileEditorThreadViewModel>(vm) {
    override val isVisible: StateFlow<Boolean> = vm.isVisible.combineState(hiddenState) { visible, hidden -> visible && !hidden }
    override val range: StateFlow<LineRange?> = postReviewRanges.combineState(vm.commentRange) { ranges, commentRange ->
      if (ranges == null || commentRange == null) return@combineState null
      val start = ReviewInEditorUtil.transferLineToAfter(ranges, commentRange.first)
      val end = ReviewInEditorUtil.transferLineToAfter(ranges, commentRange.last)
      LineRange(start, end)
    }
    override val line: StateFlow<Int?> = range.mapState { it?.end }
  }

  private inner class ShiftedNewComment(parentCs: CoroutineScope, vm: GHPRReviewFileEditorNewCommentViewModel)
    : GHPREditorMappedComponentModel.NewComment<GHPRReviewNewCommentEditorViewModel>(vm) {
    private val cs = parentCs.childScope("${this::class.simpleName}")
    override val key: Any = "NEW_${UUID.randomUUID()}"
    override val range: StateFlow<LineRange?> =
      combineState(cs, vm.position, postReviewRanges) { position, postReviewRanges ->
        if (postReviewRanges == null) return@combineState null
        position.location.toLineRange()?.transferToAfter(postReviewRanges)
      }
    override val line: StateFlow<Int?> = range.mapState { it?.end }
    override val isVisible: StateFlow<Boolean> = MutableStateFlow(true)

    override fun adjustRange(newStart: Int?, newEnd: Int?) {
      if (newStart == null && newEnd == null) return
      val ranges = postReviewRanges.value ?: emptyList()
      val transferredStart = newStart?.let {
        val startLine = ReviewInEditorUtil.transferLineFromAfter(ranges, it)?.takeIf { it >= 0 } ?: return@let null
        Side.RIGHT to startLine
      }
      val transferredEnd = newEnd?.let {
        val endLine = ReviewInEditorUtil.transferLineFromAfter(ranges, it)?.takeIf { it >= 0 } ?: return@let null
        Side.RIGHT to endLine
      }
      vm.updateLineRange(transferredStart, transferredEnd)
      vm.requestFocus()
    }
  }

  companion object {
    val KEY: Key<GHPRReviewFileEditorModel> = Key.create("GitHub.PullRequest.Editor.Review.UIModel")
  }
}

private fun GHPRReviewCommentLocation.toLineRange(): LineRange? =
  when (val loc = this) {
    is GHPRReviewCommentLocation.SingleLine -> {
      if (loc.side == Side.RIGHT) LineRange(lineIdx, lineIdx) else null
    }
    is GHPRReviewCommentLocation.MultiLine -> {
      if (loc.startSide == loc.side) LineRange(startLineIdx, lineIdx) else null
    }
  }

private fun LineRange.transferToAfter(ranges: List<Range>): LineRange {
  val start = ReviewInEditorUtil.transferLineToAfter(ranges, start)
  val end = ReviewInEditorUtil.transferLineToAfter(ranges, end)
  return LineRange(start, end)
}

private fun Range.getAfterLines(): LineRange = LineRange(start2, end2)