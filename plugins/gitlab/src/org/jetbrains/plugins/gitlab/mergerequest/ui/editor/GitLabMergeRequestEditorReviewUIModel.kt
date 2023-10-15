// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.ExcludingApproximateChangedRangesShifter
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.ex.*
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO

/**
 * A wrapper over [GitLabMergeRequestEditorReviewFileViewModel] to encapsulate LST integration
 */
internal class GitLabMergeRequestEditorReviewUIModel internal constructor(
  cs: CoroutineScope,
  private val fileVm: GitLabMergeRequestEditorReviewFileViewModel,
  private val lst: LocalLineStatusTracker<*>
) : LineStatusMarkerRangesSource<LstRange> {

  val avatarIconsProvider: IconsProvider<GitLabUserDTO> = fileVm.avatarIconsProvider

  private val localRanges = MutableStateFlow<List<LstRange>>(emptyList())

  private val reviewRanges = fileVm.changedRanges.map { LstRange(it.start2, it.end2, it.start1, it.end1) }
  private val _shiftedReviewRanges = MutableStateFlow<List<LstRange>>(emptyList())
  val shiftedReviewRanges: StateFlow<List<LstRange>> get() = _shiftedReviewRanges

  private val _commentableRanges = MutableStateFlow<List<LineRange>>(emptyList())
  val commentableRanges: StateFlow<List<LineRange>> get() = _commentableRanges

  val newDiscussions: Flow<List<GitLabMergeRequestEditorNewDiscussionViewModel>> = fileVm.newDiscussions.map {
    it.map(::ShiftedNewDiscussion)
  }
  val draftDiscussions: Flow<List<GitLabMergeRequestEditorDiscussionViewModel>> = fileVm.draftDiscussions.map {
    it.map(::ShiftedDiscussion)
  }
  val discussions: Flow<List<GitLabMergeRequestEditorDiscussionViewModel>> = fileVm.discussions.map {
    it.map(::ShiftedDiscussion)
  }

  init {
    val lstListener = ForwardingLineStatusTrackerListener(lst) { lstRanges ->
      localRanges.value = lstRanges
      _shiftedReviewRanges.value = ExcludingApproximateChangedRangesShifter.shift(reviewRanges, lstRanges)
      _commentableRanges.value = lstRanges.getUnchangedLineRanges(lst.document)
    }
    lst.addListener(lstListener)
    cs.awaitCancellationAndInvoke {
      lst.removeListener(lstListener)
    }
  }

  override fun isValid(): Boolean = true

  override fun getRanges(): List<LstRange> = _shiftedReviewRanges.value

  override fun findRange(range: LstRange): LstRange? = getRanges().find {
    it.vcsLine1 == range.vcsLine1 && it.vcsLine2 == range.vcsLine2 &&
    it.line1 == range.line1 && it.line2 == range.line2
  }

  fun requestNewDiscussion(lineIdx: Int, focus: Boolean) {
    val originalLine = transferLineFromAfter(localRanges.value, lineIdx)?.takeIf { it >= 0 } ?: return
    fileVm.requestNewDiscussion(originalLine, focus)
  }

  fun cancelNewDiscussion(lineIdx: Int) {
    val originalLine = transferLineFromAfter(localRanges.value, lineIdx)?.takeIf { it >= 0 } ?: return
    fileVm.cancelNewDiscussion(originalLine)
  }

  fun getOriginalContent(lines: LineRange): String? {
    return fileVm.getOriginalContent(lines)
  }

  fun showDiff(lineIdx: Int?) {
    val originalLine = lineIdx?.let { transferLineFromAfter(localRanges.value, it, true) }?.takeIf { it >= 0 }
    fileVm.showDiff(originalLine)
  }

  private inner class ShiftedDiscussion(private val vm: GitLabMergeRequestEditorDiscussionViewModel)
    : GitLabMergeRequestEditorDiscussionViewModel by vm {
    override val isVisible: Flow<Boolean> = vm.isVisible
    override val line: Flow<Int?> = localRanges.combine(vm.line) { ranges, line ->
      line?.let { transferLineToAfter(ranges, it) }?.takeIf { it >= 0 }
    }
  }

  private inner class ShiftedNewDiscussion(private val vm: GitLabMergeRequestEditorNewDiscussionViewModel)
    : GitLabMergeRequestEditorNewDiscussionViewModel by vm {
    override val isVisible: Flow<Boolean> = flowOf(true)
    override val line: Flow<Int?> = localRanges.combine(vm.line) { ranges, line ->
      line?.let { transferLineToAfter(ranges, it) }?.takeIf { it >= 0 }
    }
  }

  companion object {
    val KEY: Key<GitLabMergeRequestEditorReviewUIModel> = Key.create("GitLab.MergeRequest.Editor.Review.UIModel")
  }
}

private fun transferLineToAfter(ranges: List<LstRange>, line: Int): Int {
  if (ranges.isEmpty()) return line
  var result = line
  for (range in ranges) {
    if (line in range.vcsLine1 until range.vcsLine2) {
      return (range.line2 - 1).coerceAtLeast(0)
    }

    if (range.vcsLine2 > line) return result

    val length1 = range.vcsLine2 - range.vcsLine1
    val length2 = range.line2 - range.line1
    result += length2 - length1
  }
  return result
}

private fun transferLineFromAfter(ranges: List<LstRange>, line: Int, approximate: Boolean = false): Int? {
  if (ranges.isEmpty()) return line
  var result = line
  for (range in ranges) {
    if (line < range.line1) return result

    if (line in range.line1 until range.line2) {
      return if (approximate) range.vcsLine2 else null
    }

    val length1 = range.vcsLine2 - range.vcsLine1
    val length2 = range.line2 - range.line1
    result -= length2 - length1
  }
  return result
}

private fun List<LstRange>.getUnchangedLineRanges(document: Document): List<LineRange> {
  val lineCount = DiffUtil.getLineCount(document)
  if (isEmpty()) return listOf(LineRange(0, lineCount))
  val result = mutableListOf<LineRange>()
  var lastChangeEnd: Int? = null

  forEach {
    val lastEnd = lastChangeEnd
    if (lastEnd == null) {
      if (it.line1 != 0) {
        result.add(LineRange(0, it.line1))
      }
      lastChangeEnd = it.line2
    }
    else if (it.line1 == lastEnd) {
      lastChangeEnd = it.line2
    }
    else {
      result.add(LineRange(lastEnd, it.line1))
      lastChangeEnd = it.line2
    }
  }

  val lastEnd = lastChangeEnd!!
  if (lastEnd != lineCount) {
    result.add(LineRange(lastEnd, lineCount))
  }
  return result
}

/**
 * Listens to [lineStatusTracker] and sends updated ranges to [onRangesChanged]
 */
private class ForwardingLineStatusTrackerListener(
  private val lineStatusTracker: LineStatusTrackerI<*>,
  private val onRangesChanged: (List<LstRange>) -> Unit
) : LineStatusTrackerListener {
  init {
    val ranges = lineStatusTracker.getRanges()
    onRangesChanged(ranges.orEmpty())
  }

  override fun onOperationalStatusChange() {
    val ranges = lineStatusTracker.getRanges()
    if (ranges != null) {
      onRangesChanged(ranges)
    }
  }

  override fun onRangesChanged() {
    val ranges = lineStatusTracker.getRanges()
    if (ranges != null) {
      onRangesChanged(ranges)
    }
  }
}