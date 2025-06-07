// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.mapModelsToViewModels
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.ui.codereview.editor.*
import com.intellij.collaboration.util.ExcludingApproximateChangedRangesShifter
import com.intellij.collaboration.util.Hideable
import com.intellij.collaboration.util.syncOrToggleAll
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Key
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings

internal class GHPRReviewFileEditorModel internal constructor(
  private val cs: CoroutineScope,
  private val settings: GithubPullRequestsProjectUISettings,
  private val fileVm: GHPRReviewFileEditorViewModel,
  private val changesModel: MutableCodeReviewEditorGutterChangesModel = MutableCodeReviewEditorGutterChangesModel()
) : CodeReviewEditorGutterChangesModel by changesModel,
    CodeReviewEditorGutterActionableChangesModel,
    CodeReviewEditorModel<GHPREditorMappedComponentModel> {

  private val postReviewRanges = MutableStateFlow<List<Range>?>(null)

  override var shouldHighlightDiffRanges: Boolean by settings::highlightDiffLinesInEditor

  override val gutterControlsState: StateFlow<CodeReviewEditorGutterControlsModel.ControlsState?> =
    combine(postReviewRanges, fileVm.linesWithComments, fileVm.newComments) { postReviewRanges, linesWithComments, newComments ->
      if (postReviewRanges != null) {
        val shiftedLinesWithComments = linesWithComments.mapTo(mutableSetOf()) {
          ReviewInEditorUtil.transferLineToAfter(postReviewRanges, it)
        }
        val shiftedCommentableRanges = ExcludingApproximateChangedRangesShifter.shift(fileVm.commentableRanges, postReviewRanges).map {
          it.getAfterLines()
        }
        val linesWithNewComments = newComments.mapTo(mutableSetOf()) {
          ReviewInEditorUtil.transferLineToAfter(postReviewRanges, it.line)
        }
        GHPRReviewEditorGutterControlsState(shiftedLinesWithComments, linesWithNewComments, shiftedCommentableRanges)
      }
      else null
    }.stateInNow(cs, null)

  override val inlays: StateFlow<Collection<GHPREditorMappedComponentModel>> = combine(
    fileVm.threads.mapModelsToViewModels { ShiftedThread(it) },
    fileVm.newComments.mapModelsToViewModels { ShiftedNewComment(it) },
  ) { threads, new ->
    threads + new
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

  private fun StateFlow<Int?>.shiftLine(): StateFlow<Int?> =
    combineState(postReviewRanges) { line, ranges ->
      if (ranges != null && line != null) {
        ReviewInEditorUtil.transferLineToAfter(ranges, line).takeIf { it >= 0 }
      }
      else null
    }

  private inner class ShiftedThread(vm: GHPRReviewFileEditorThreadViewModel)
    : GHPREditorMappedComponentModel.Thread<GHPRReviewFileEditorThreadViewModel>(vm) {
    override val isVisible: StateFlow<Boolean> = vm.isVisible.combineState(hiddenState) { visible, hidden -> visible && !hidden }
    override val line: StateFlow<Int?> = vm.line.shiftLine()
  }

  private inner class ShiftedNewComment(vm: GHPRReviewFileEditorNewCommentViewModel)
    : GHPREditorMappedComponentModel.NewComment<GHPRReviewNewCommentEditorViewModel>(vm) {
    private val originalLine = vm.line
    override val key: Any = "NEW_$originalLine"
    override val isVisible: StateFlow<Boolean> = MutableStateFlow(true)
    override val line: StateFlow<Int?> = postReviewRanges.mapState { ranges ->
      if (ranges != null) {
        ReviewInEditorUtil.transferLineToAfter(ranges, originalLine).takeIf { it >= 0 }
      }
      else null
    }
  }

  companion object {
    val KEY: Key<GHPRReviewFileEditorModel> = Key.create("GitHub.PullRequest.Editor.Review.UIModel")
  }
}

private fun Range.getAfterLines(): LineRange = LineRange(start2, end2)