// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.ui.codereview.editor.EditorMapped
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.ExcludingApproximateChangedRangesShifter
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
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO

/**
 * A wrapper over [GitLabMergeRequestEditorReviewFileViewModel] to encapsulate LST integration
 */
internal class GitLabMergeRequestEditorReviewUIModel internal constructor(
  cs: CoroutineScope,
  private val fileVm: GitLabMergeRequestEditorReviewFileViewModel,
  document: Document
) : LineStatusMarkerRangesSource<LstRange>, Disposable {

  private val reviewHeadDocument = LineStatusTrackerBase.createVcsDocument(document)
  private val documentTracker = DocumentTracker(reviewHeadDocument, document).also {
    Disposer.register(this, it)
  }
  private var trackerInitialized = false

  val avatarIconsProvider: IconsProvider<GitLabUserDTO> = fileVm.avatarIconsProvider

  private val postReviewRanges = MutableStateFlow<List<Range>>(emptyList())

  private val _shiftedReviewRanges = MutableStateFlow<List<LstRange>>(emptyList())
  val shiftedReviewRanges: StateFlow<List<LstRange>> get() = _shiftedReviewRanges

  private val _nonCommentableRanges = MutableStateFlow<List<LineRange>>(emptyList())
  val nonCommentableRanges: StateFlow<List<LineRange>> get() = _nonCommentableRanges

  private fun updateRanges() {
    if (!trackerInitialized) return
    postReviewRanges.value = documentTracker.blocks.map { it.range }
    _shiftedReviewRanges.value = ExcludingApproximateChangedRangesShifter
      .shift(fileVm.changedRanges, postReviewRanges.value).map(Range::asLst)
    _nonCommentableRanges.value = postReviewRanges.value.map(Range::getAfterLines)
  }

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

  init {
    cs.launchNow {
      fileVm.originalContent.collectLatest { result ->
        result?.getOrNull()?.also {
          setReviewHeadContent(it)
          updateRanges()
        }
      }
    }

    documentTracker.addHandler(object : DocumentTracker.Handler {
      override fun afterBulkRangeChange(isDirty: Boolean) {
        updateRanges()
      }
    })
  }

  val newDiscussions: StateFlow<List<ShiftedNewDiscussion>> = fileVm.newDiscussions.map {
    it.map(::ShiftedNewDiscussion)
  }.stateInNow(cs, emptyList())
  val draftDiscussions: StateFlow<List<ShiftedDraftNote>> = fileVm.draftNotes.map {
    it.map(::ShiftedDraftNote)
  }.stateInNow(cs, emptyList())
  val discussions: StateFlow<List<ShiftedDiscussion>> = fileVm.discussions.map {
    it.map(::ShiftedDiscussion)
  }.stateInNow(cs, emptyList())

  override fun isValid(): Boolean = true

  override fun getRanges(): List<LstRange> = _shiftedReviewRanges.value

  override fun findRange(range: LstRange): LstRange? = getRanges().find {
    it.vcsLine1 == range.vcsLine1 && it.vcsLine2 == range.vcsLine2 &&
    it.line1 == range.line1 && it.line2 == range.line2
  }

  fun requestNewDiscussion(lineIdx: Int, focus: Boolean) {
    val originalLine = transferLineFromAfter(postReviewRanges.value, lineIdx)?.takeIf { it >= 0 } ?: return
    fileVm.requestNewDiscussion(originalLine, focus)
  }

  fun cancelNewDiscussion(originalLine: Int) {
    fileVm.cancelNewDiscussion(originalLine)
  }

  fun getOriginalContent(lines: LineRange): String? {
    return fileVm.getOriginalContent(lines)
  }

  fun showDiff(lineIdx: Int?) {
    val originalLine = lineIdx?.let { transferLineFromAfter(postReviewRanges.value, it, true) }?.takeIf { it >= 0 }
    fileVm.showDiff(originalLine)
  }

  inner class ShiftedDiscussion(val vm: GitLabMergeRequestEditorDiscussionViewModel) : EditorMapped {
    override val isVisible: StateFlow<Boolean> = vm.isVisible
    override val line: StateFlow<Int?> = postReviewRanges.combineState(vm.line) { ranges, line ->
      line?.let { transferLineToAfter(ranges, it) }?.takeIf { it >= 0 }
    }
  }

  inner class ShiftedDraftNote(val vm: GitLabMergeRequestEditorDraftNoteViewModel) : EditorMapped {
    override val isVisible: StateFlow<Boolean> = vm.isVisible
    override val line: StateFlow<Int?> = postReviewRanges.combineState(vm.line) { ranges, line ->
      line?.let { transferLineToAfter(ranges, it) }?.takeIf { it >= 0 }
    }
  }

  inner class ShiftedNewDiscussion(val vm: GitLabMergeRequestEditorNewDiscussionViewModel) : EditorMapped {
    override val isVisible: StateFlow<Boolean> = MutableStateFlow(true)
    override val line: StateFlow<Int?> = postReviewRanges.combineState(vm.line) { ranges, line ->
      line?.let { transferLineToAfter(ranges, it) }?.takeIf { it >= 0 }
    }
  }

  override fun dispose() = Unit

  companion object {
    val KEY: Key<GitLabMergeRequestEditorReviewUIModel> = Key.create("GitLab.MergeRequest.Editor.Review.UIModel")
  }
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