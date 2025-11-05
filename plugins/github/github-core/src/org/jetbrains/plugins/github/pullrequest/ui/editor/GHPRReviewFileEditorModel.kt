// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.async.*
import com.intellij.collaboration.ui.codereview.editor.*
import com.intellij.collaboration.util.ExcludingApproximateChangedRangesShifter
import com.intellij.collaboration.util.Hideable
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.syncOrToggleAll
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Key
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation

internal class GHPRReviewFileEditorModel internal constructor(
  private val cs: CoroutineScope,
  private val settings: GithubPullRequestsProjectUISettings,
  private val fileVm: GHPRReviewFileEditorViewModel,
  private val changesModel: MutableCodeReviewEditorGutterChangesModel = MutableCodeReviewEditorGutterChangesModel(),
  @RequiresEdt private val showEditor: (RefComparisonChange, Int) -> Unit,
) : CodeReviewEditorGutterChangesModel by changesModel,
    CodeReviewEditorGutterActionableChangesModel,
    CodeReviewEditorModel<GHPREditorMappedComponentModel>,
    CodeReviewNavigableEditorViewModel,
    CodeReviewEditorGutterControlsModel.WithMultilineComments {

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

  private fun StateFlow<Int?>.shiftLine(): StateFlow<Int?> =
    combineState(postReviewRanges) { line, ranges ->
      if (ranges != null && line != null) {
        ReviewInEditorUtil.transferLineToAfter(ranges, line).takeIf { it >= 0 }
      }
      else null
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

  override fun updateCommentLines(oldLineRange: LineRange, newLineRange: LineRange) {
    val ranges = postReviewRanges.value ?: return
    val originalStartLine = ReviewInEditorUtil.transferLineFromAfter(ranges, oldLineRange.start)?.takeIf { it >= 0 } ?: return
    val originalEndLine = ReviewInEditorUtil.transferLineFromAfter(ranges, oldLineRange.end)?.takeIf { it >= 0 } ?: return
    val newStartLine = ReviewInEditorUtil.transferLineFromAfter(ranges, newLineRange.start)?.takeIf { it >= 0 } ?: return
    val newEndLine = ReviewInEditorUtil.transferLineFromAfter(ranges, newLineRange.end)?.takeIf { it >= 0 } ?: return

    fileVm.updateCommentLines(LineRange(originalStartLine, originalEndLine), LineRange(newStartLine, newEndLine))
  }

  override fun canCreateComment(lineRange: LineRange): Boolean {
    val gutterControlState = gutterControlsState.value ?: return false
    return (lineRange.start..lineRange.end).all {
      gutterControlState.isLineCommentable(it)
    }
  }

  private inner class ShiftedThread(vm: GHPRReviewFileEditorThreadViewModel)
    : GHPREditorMappedComponentModel.Thread<GHPRReviewFileEditorThreadViewModel>(vm) {
    override val isVisible: StateFlow<Boolean> = vm.isVisible.combineState(hiddenState) { visible, hidden -> visible && !hidden }
    override val line: StateFlow<Int?> = vm.line.shiftLine()
    override val range: StateFlow<Pair<Side, IntRange>?> = postReviewRanges.combineState(vm.commentRange) { ranges, commentRange ->
      if (ranges == null || commentRange == null) return@combineState null
      val start = ReviewInEditorUtil.transferLineToAfter(ranges, commentRange.first)
      val end = ReviewInEditorUtil.transferLineToAfter(ranges, commentRange.last)
      Side.RIGHT to start..end
    }
  }

  private inner class ShiftedNewComment(cs: CoroutineScope, vm: GHPRReviewFileEditorNewCommentViewModel)
    : GHPREditorMappedComponentModel.NewComment<GHPRReviewNewCommentEditorViewModel>(vm) {
    private val location: StateFlow<GHPRReviewCommentLocation> = vm.position.mapState { it.location }
    override val key: Any = "NEW_${location.value}"
    override val isVisible: StateFlow<Boolean> = MutableStateFlow(true)

    private val cs = cs.childScope("${this::class.simpleName}")
    private val _range = MutableStateFlow(
      when (val loc = location.value) {
        is GHPRReviewCommentLocation.SingleLine -> {
          Side.RIGHT to loc.lineIdx.shiftLineToAfter().let { it..it }
        }
        is GHPRReviewCommentLocation.MultiLine -> {
          Side.RIGHT to (loc.startLineIdx.shiftLineToAfter()..loc.lineIdx.shiftLineToAfter())
        }
      }
    )
    override val range: StateFlow<Pair<Side, IntRange>?> = _range.asStateFlow()
    override val line: StateFlow<Int?> = location.mapState { it.lineIdx.shiftLineToAfter() }

    private val manualRange = MutableStateFlow(range.value)
    private var isManualUpdate = false

    override fun setRange(range: Pair<Side, IntRange>?) {
      if (manualRange.value == range) return
      isManualUpdate = true
      manualRange.value = range
    }

    init {
      this.cs.launch {
        postReviewRanges.collectLatest { ranges ->
          if (ranges != null && !isManualUpdate) {
            val logical = manualRange.value?.second
            if (logical != null) _range.value = adjustRange(ranges)
          }
        }
      }
      this.cs.launch {
        manualRange.collectLatest { newRange ->
          if (newRange != null) {
            _range.value = newRange
            isManualUpdate = false
          }
        }
      }
    }

    private fun adjustRange(ranges: List<Range>): Pair<Side, IntRange> {
      val loc = (vm as GHPRReviewFileEditorNewCommentViewModel).location.value
      val startSrc = (loc as? GHPRReviewCommentLocation.MultiLine)?.startLineIdx ?: loc.lineIdx
      val start = ReviewInEditorUtil.transferLineToAfter(ranges, startSrc)
      val end = ReviewInEditorUtil.transferLineToAfter(ranges, loc.lineIdx)
      return Side.RIGHT to start..end
    }
  }

  companion object {
    val KEY: Key<GHPRReviewFileEditorModel> = Key.create("GitHub.PullRequest.Editor.Review.UIModel")
  }
}

private fun Range.getAfterLines(): LineRange = LineRange(start2, end2)