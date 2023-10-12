// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.collaboration.ui.codereview.editor.EditorMapped
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.util.ExcludingApproximateChangedRangesShifter
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vcs.ex.*
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.review.GitLabMergeRequestChangeViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiffDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel

/**
 * A wrapper over [GitLabMergeRequestChangeViewModel] to encapsulate LST integration
 */
internal class GitLabMergeRequestEditorReviewUIModel internal constructor(
  cs: CoroutineScope,
  private val fileVm: GitLabMergeRequestChangeViewModel,
  private val lst: LocalLineStatusTracker<*>
) : LineStatusMarkerRangesSource<LstRange> {

  val avatarIconsProvider: IconsProvider<GitLabUserDTO> = fileVm.avatarIconsProvider

  private val localRanges = MutableStateFlow<List<LstRange>>(emptyList())

  private val reviewRanges = fileVm.changedRanges.map { LstRange(it.start2, it.end2, it.start1, it.end1) }
  private val _shiftedReviewRanges = MutableStateFlow<List<LstRange>>(emptyList())
  val shiftedReviewRanges: StateFlow<List<LstRange>> get() = _shiftedReviewRanges

  private val _commentableRanges = MutableStateFlow<List<LineRange>>(emptyList())
  val commentableRanges: StateFlow<List<LineRange>> get() = _commentableRanges

  val newDiscussions: Flow<List<MappedNewDiscussion>> = fileVm.newDiscussions.map {
    it.map { (location, vm) -> MappedNewDiscussion(location, vm) }
  }
  val draftDiscussions: Flow<List<MappedDiscussion>> = fileVm.draftDiscussions.map {
    it.map(::MappedDiscussion)
  }
  val discussions: Flow<List<MappedDiscussion>> = fileVm.discussions.map {
    it.map(::MappedDiscussion)
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
    val location = mapLine(localRanges.value, lineIdx) ?: return
    fileVm.requestNewDiscussion(location, focus)
  }

  fun cancelNewDiscussion(location: DiffLineLocation) {
    fileVm.cancelNewDiscussion(location)
  }

  fun getOriginalContent(lines: LineRange): String? {
    return fileVm.getOriginalContent(lines)
  }

  inner class MappedDiscussion(val vm: GitLabMergeRequestDiffDiscussionViewModel) : EditorMapped {
    override val isVisible: Flow<Boolean> = vm.isVisible
    override val line: Flow<Int?> = localRanges.combine(vm.location) { ranges, location ->
      location?.let {
        mapLocation(ranges, it)
      }
    }
  }

  inner class MappedNewDiscussion(val location: DiffLineLocation, val vm: NewGitLabNoteViewModel) : EditorMapped {
    override val isVisible: Flow<Boolean> = flowOf(true)
    override val line: Flow<Int?> = localRanges.map { ranges -> mapLocation(ranges, location) }
  }
}

private fun mapLocation(ranges: List<LstRange>, location: DiffLineLocation): Int? {
  val (side, line) = location
  return if (side == Side.RIGHT) transferLineToAfter(ranges, line).takeIf { it >= 0 } else null
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

private fun mapLine(ranges: List<LstRange>, lineIdx: Int): DiffLineLocation? {
  val oldLine = transferLineFromAfter(ranges, lineIdx)?.takeIf { it >= 0 } ?: return null
  return Side.RIGHT to oldLine
}

private fun transferLineFromAfter(ranges: List<LstRange>, line: Int): Int? {
  if (ranges.isEmpty()) return line
  var result = line
  for (range in ranges) {
    if (line < range.line1) return result

    if (line in range.line1 until range.line2) {
      return null
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