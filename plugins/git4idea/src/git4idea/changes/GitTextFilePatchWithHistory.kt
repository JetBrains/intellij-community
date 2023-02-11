// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil
import com.intellij.openapi.diff.impl.patch.TextFilePatch

sealed class GitTextFilePatchWithHistory(val patch: TextFilePatch, protected val fileHistory: GitFileHistory) {

  val diffRanges: List<Range> by lazy(LazyThreadSafetyMode.NONE) {
    patch.hunks.map(PatchHunkUtil::getRange)
  }
  val diffRangesWithoutContext: List<Range> by lazy(LazyThreadSafetyMode.NONE) {
    patch.hunks.map(PatchHunkUtil::getChangeOnlyRanges).flatten()
  }

  fun contains(commitSha: String, filePath: String): Boolean {
    return fileHistory.contains(commitSha, filePath)
  }

  class Commit(patch: TextFilePatch, fileHistory: GitFileHistory) : GitTextFilePatchWithHistory(patch, fileHistory) {

    fun mapPosition(fromCommitSha: String,
                    side: Side, line: Int): DiffLineLocation? {

      val comparison = fileHistory.compare(fromCommitSha, patch.afterVersionId!!)
      if (comparison == 0) return DiffLineLocation(side, line)
      if (comparison < 0) {
        val patches = fileHistory.getPatches(fromCommitSha, patch.afterVersionId!!, false, true)
        return transferLine(patches, side, line, false)
      }
      else {
        val patches = fileHistory.getPatches(patch.afterVersionId!!, fromCommitSha, true, false)
        return transferLine(patches, side, line, true)
      }
    }

    private fun transferLine(patchChain: List<TextFilePatch>, side: Side, line: Int, rightToLeft: Boolean): DiffLineLocation? {
      // points to the same patch
      if (patchChain.isEmpty()) return DiffLineLocation(side, line)

      val patches = if (rightToLeft) patchChain.asReversed() else patchChain
      val transferFrom = if (rightToLeft) Side.RIGHT else Side.LEFT

      var currentSide: Side = side
      var currentLine: Int = line

      for (patch in patches) {
        if (currentSide == transferFrom) {
          val changeOnlyRanges = patch.hunks.map { hunk ->
            val ranges = PatchHunkUtil.getChangeOnlyRanges(hunk)
            if (rightToLeft) ranges.map { reverseRange(it) } else ranges
          }.flatten()

          var offset = 0
          loop@ for (range in changeOnlyRanges) {
            when {
              currentLine < range.start1 ->
                break@loop
              currentLine in range.start1 until range.end1 ->
                return null
              currentLine >= range.end1 ->
                offset += (range.end2 - range.start2) - (range.end1 - range.start1)
            }
          }
          currentLine += offset
        }
        else {
          currentSide = transferFrom
        }
      }
      return DiffLineLocation(currentSide, currentLine)
    }

    private fun reverseRange(range: Range) = Range(range.start2, range.end2, range.start1, range.end1)
  }

  class Cumulative(patch: TextFilePatch, fileHistory: GitFileHistory) : GitTextFilePatchWithHistory(patch, fileHistory)
}