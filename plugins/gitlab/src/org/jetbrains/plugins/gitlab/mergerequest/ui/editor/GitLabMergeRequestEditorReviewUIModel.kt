// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapModelsToViewModels
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorGutterControlsModel
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorInlaysModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.ExcludingApproximateChangedRangesShifter
import com.intellij.collaboration.util.Hideable
import com.intellij.collaboration.util.getOrNull
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.ex.DocumentTracker
import com.intellij.openapi.vcs.ex.LineStatusMarkerRangesSource
import com.intellij.openapi.vcs.ex.LineStatusTrackerBase
import com.intellij.openapi.vcs.ex.LstRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO

/**
 * A wrapper over [GitLabMergeRequestEditorReviewFileViewModel] to encapsulate LST integration
 */
internal class GitLabMergeRequestEditorReviewUIModel internal constructor(
  cs: CoroutineScope,
  private val fileVm: GitLabMergeRequestEditorReviewFileViewModel,
  document: Document
) : LineStatusMarkerRangesSource<LstRange>,
    CodeReviewEditorInlaysModel<GitLabMergeRequestEditorMappedComponentModel>,
    CodeReviewEditorGutterControlsModel,
    Disposable {

  private val reviewHeadDocument = LineStatusTrackerBase.createVcsDocument(document)
  private val documentTracker = DocumentTracker(reviewHeadDocument, document).also {
    Disposer.register(this, it)
  }
  private var trackerInitialized = false

  val avatarIconsProvider: IconsProvider<GitLabUserDTO> = fileVm.avatarIconsProvider

  private val postReviewRanges = MutableStateFlow<List<Range>?>(null)

  private val _shiftedReviewRanges = MutableStateFlow<List<LstRange>>(emptyList())
  val shiftedReviewRanges: StateFlow<List<LstRange>> get() = _shiftedReviewRanges

  override val gutterControlsState: StateFlow<CodeReviewEditorGutterControlsModel.ControlsState?> =
    combine(postReviewRanges, fileVm.canComment, fileVm.linesWithDiscussions) { postReviewRanges, canComment, linesWithDiscussions ->
      if (postReviewRanges != null) {
        val nonCommentableRanges = postReviewRanges.map(Range::getAfterLines)
        val shiftedLinesWithDiscussions = linesWithDiscussions.mapTo(mutableSetOf()) {
          transferLineToAfter(postReviewRanges, it)
        }
        GutterState(shiftedLinesWithDiscussions, canComment, nonCommentableRanges)
      }
      else null
    }.stateInNow(cs, null)

  private fun setReviewHeadContent(content: CharSequence) {
    documentTracker.doFrozen(Side.LEFT) {
      reviewHeadDocument.setReadOnly(false)
      try {
        CommandProcessor.getInstance().runUndoTransparentAction {
          reviewHeadDocument.setText(content)
        }
      }
      finally {
        reviewHeadDocument.setReadOnly(true)
      }
    }
    trackerInitialized = true
  }

  private fun updateTrackerRanges() {
    if (trackerInitialized) {
      val trackerRanges = documentTracker.blocks.map { it.range }
      postReviewRanges.value = trackerRanges
      _shiftedReviewRanges.value = ExcludingApproximateChangedRangesShifter.shift(fileVm.changedRanges, trackerRanges).map(Range::asLst)
    }
  }

  init {
    cs.launchNow {
      fileVm.originalContent.collectLatest { result ->
        result?.getOrNull()?.also {
          setReviewHeadContent(it)
          updateTrackerRanges()
        }
      }
    }

    documentTracker.addHandler(object : DocumentTracker.Handler {
      override fun afterBulkRangeChange(isDirty: Boolean) {
        updateTrackerRanges()
      }
    })
  }

  override val inlays: StateFlow<Collection<GitLabMergeRequestEditorMappedComponentModel>> = combine(
    fileVm.discussions.mapModelsToViewModels { ShiftedDiscussion(it) },
    fileVm.draftNotes.mapModelsToViewModels { ShiftedDraftNote(it) },
    fileVm.newDiscussions.mapModelsToViewModels { ShiftedNewDiscussion(it) }
  ) { discussions, drafts, new ->
    discussions + drafts + new
  }.stateInNow(cs, emptyList())

  override fun isValid(): Boolean = true

  override fun getRanges(): List<LstRange> = _shiftedReviewRanges.value

  override fun findRange(range: LstRange): LstRange? = getRanges().find {
    it.vcsLine1 == range.vcsLine1 && it.vcsLine2 == range.vcsLine2 &&
    it.line1 == range.line1 && it.line2 == range.line2
  }

  override fun requestNewComment(lineIdx: Int) {
    val ranges = postReviewRanges.value ?: return
    val originalLine = transferLineFromAfter(ranges, lineIdx)?.takeIf { it >= 0 } ?: return
    fileVm.requestNewDiscussion(originalLine, true)
  }

  override fun toggleComments(lineIdx: Int) {
    inlays.value.forEach {
      if (it is Hideable) {
        if (it.line.value == lineIdx) {
          it.toggleHidden()
        }
      }
    }
  }

  fun cancelNewDiscussion(originalLine: Int) {
    fileVm.cancelNewDiscussion(originalLine)
  }

  fun getOriginalContent(lines: LineRange): String? {
    return fileVm.getOriginalContent(lines)
  }

  fun showDiff(lineIdx: Int?) {
    val ranges = postReviewRanges.value ?: return
    val originalLine = lineIdx?.let { transferLineFromAfter(ranges, it, true) }?.takeIf { it >= 0 }
    fileVm.showDiff(originalLine)
  }

  private fun StateFlow<Int?>.shiftLine(): StateFlow<Int?> =
    combineState(postReviewRanges) { line, ranges ->
      if (ranges != null && line != null) {
        transferLineToAfter(ranges, line).takeIf { it >= 0 }
      }
      else null
    }

  private inner class ShiftedDiscussion(vm: GitLabMergeRequestEditorDiscussionViewModel)
    : GitLabMergeRequestEditorMappedComponentModel.Discussion<GitLabMergeRequestEditorDiscussionViewModel>(vm) {
    override val isVisible: StateFlow<Boolean> = vm.isVisible.combineState(hidden) { visible, hidden -> visible && !hidden }
    override val line: StateFlow<Int?> = vm.line.shiftLine()
  }

  private inner class ShiftedDraftNote(vm: GitLabMergeRequestEditorDraftNoteViewModel)
    : GitLabMergeRequestEditorMappedComponentModel.DraftNote<GitLabMergeRequestEditorDraftNoteViewModel>(vm) {
    override val isVisible: StateFlow<Boolean> = vm.isVisible.combineState(hidden) { visible, hidden -> visible && !hidden }
    override val line: StateFlow<Int?> = vm.line.shiftLine()
  }

  private inner class ShiftedNewDiscussion(vm: GitLabMergeRequestEditorNewDiscussionViewModel)
    : GitLabMergeRequestEditorMappedComponentModel.NewDiscussion<GitLabMergeRequestEditorNewDiscussionViewModel>(vm) {
    override val key: Any = "NEW_${vm.originalLine}"
    override val isVisible: StateFlow<Boolean> = MutableStateFlow(true)
    override val line: StateFlow<Int?> = vm.line.shiftLine()
    override fun cancel() = cancelNewDiscussion(vm.originalLine)
  }

  override fun dispose() = Unit

  companion object {
    val KEY: Key<GitLabMergeRequestEditorReviewUIModel> = Key.create("GitLab.MergeRequest.Editor.Review.UIModel")
  }
}

private data class GutterState(
  override val linesWithComments: Set<Int>,
  val canComment: Boolean,
  val nonCommentableRanges: List<LineRange>
) : CodeReviewEditorGutterControlsModel.ControlsState {
  override fun isLineCommentable(lineIdx: Int): Boolean =
    canComment && nonCommentableRanges.none { lineIdx in it.start until it.end }
}

private fun transferLineToAfter(ranges: List<Range>, line: Int): Int {
  if (ranges.isEmpty()) return line
  var result = line
  for (range in ranges) {
    if (line in range.start1 until range.end1) {
      return (range.end2 - 1).coerceAtLeast(0)
    }

    if (range.end1 > line) return result

    val length1 = range.end1 - range.start1
    val length2 = range.end2 - range.start2
    result += length2 - length1
  }
  return result
}

private fun transferLineFromAfter(ranges: List<Range>, line: Int, approximate: Boolean = false): Int? {
  if (ranges.isEmpty()) return line
  var result = line
  for (range in ranges) {
    if (line < range.start2) return result

    if (line in range.start2 until range.end2) {
      return if (approximate) range.end1 else null
    }

    val length1 = range.end1 - range.start1
    val length2 = range.end2 - range.start2
    result -= length2 - length1
  }
  return result
}

private fun Range.getAfterLines(): LineRange = LineRange(start2, end2)
private fun Range.asLst(): LstRange = LstRange(start2, end2, start1, end1)