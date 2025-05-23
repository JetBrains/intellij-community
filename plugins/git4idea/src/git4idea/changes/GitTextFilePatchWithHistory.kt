// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import org.jetbrains.annotations.ApiStatus
import kotlin.math.floor

/**
 * Holds the [patch] between two commits in [fileHistory]
 *
 * @param isCumulative [true] if [patch] is between start and end of [fileHistory] which spans from merge base to last commit,
 *                     [false] if it is a patch between a pair of adjacent commits in [fileHistory]
 */
@ApiStatus.Experimental
class GitTextFilePatchWithHistory(val patch: TextFilePatch, val isCumulative: Boolean, val fileHistory: GitFileHistory) {

  /**
   * Check that history contains the specified file at the [commitSha]
   * File may have not been changed in this particular commit, but was changed before or after
   */
  fun contains(commitSha: String, filePath: String): Boolean = fileHistory.contains(commitSha, filePath)

  /**
   * @return `null` only if the commit cannot be found.
   * Otherwise, this function returns the most accurate line number on the given side of the patch history we could derive.
   * If the line is removed during any patch, its location information can no longer be retrieved exactly, so instead, we
   * continue with the topmost line of the changed hunk.
   */
  fun forcefullyMapLine(fromCommitSha: String, lineIndex: Int, side: Side): Int? {
    // map to merge base, not left revision
    val beforeSha = if (isCumulative) fileHistory.findStartCommit()!! else patch.beforeVersionId!!
    val afterSha = patch.afterVersionId!!

    if (fromCommitSha == beforeSha && side == Side.LEFT) return lineIndex
    if (fromCommitSha == afterSha && side == Side.RIGHT) return lineIndex

    return when (side) {
      Side.LEFT -> {
        val patches = fileHistory.getPatchesBetween(beforeSha, fromCommitSha)
        transferLine(patches, lineIndex, rightToLeft = true).line
      }
      Side.RIGHT -> {
        val patches = fileHistory.getPatchesBetween(fromCommitSha, afterSha)
        transferLine(patches, lineIndex, rightToLeft = false).line
      }
    }
  }

  /**
   * Map the [lineIndex] in a file in a *commit* [fromCommitSha] to a location in the current [patch]
   *
   * @param fromCommitSha commit where the line index is known
   * @param lineIndex index of the text line in a file
   * @param bias priority of mapping to the parent or child of commit [fromCommitSha]
   */
  fun mapLine(fromCommitSha: String, lineIndex: Int, bias: Side): DiffLineLocation? {
    // map to merge base, not left revision
    val beforeSha = if (isCumulative) fileHistory.findStartCommit()!! else patch.beforeVersionId!!
    val afterSha = patch.afterVersionId!!

    if (fromCommitSha == beforeSha) return DiffLineLocation(Side.LEFT, lineIndex)
    if (fromCommitSha == afterSha) return DiffLineLocation(Side.RIGHT, lineIndex)

    if (!fileHistory.contains(fromCommitSha, patch.filePath)) return null

    return try {
      when (bias) {
        Side.LEFT -> transferToParent(lineIndex, beforeSha, fromCommitSha, afterSha)
                     ?: transferToChild(lineIndex, beforeSha, fromCommitSha, afterSha)
        Side.RIGHT -> transferToChild(lineIndex, beforeSha, fromCommitSha, afterSha)
                      ?: transferToParent(lineIndex, beforeSha, fromCommitSha, afterSha)
      }
    }
    catch (e: Exception) {
      LOG.debug(e)
      null
    }
  }

  private fun transferToParent(lineIndex: Int, beforeSha: String, fromCommitSha: String, afterSha: String): DiffLineLocation? =
    if (fileHistory.compare(afterSha, fromCommitSha) < 0) {
      val patches = fileHistory.getPatchesBetween(afterSha, fromCommitSha)
      transferLine(patches, lineIndex, true).exactLocation()?.let {
        Side.RIGHT to it
      }
    }
    else if (fileHistory.compare(beforeSha, fromCommitSha) < 0) {
      val patches = fileHistory.getPatchesBetween(beforeSha, fromCommitSha)
      transferLine(patches, lineIndex, true).exactLocation()?.let {
        Side.LEFT to it
      }
    }
    else {
      error("Couldn't find commit ${fromCommitSha}")
    }

  private fun transferToChild(lineIndex: Int, beforeSha: String, fromCommitSha: String, afterSha: String): DiffLineLocation? =
    if (fileHistory.compare(fromCommitSha, beforeSha) < 0) {
      val patches = fileHistory.getPatchesBetween(fromCommitSha, beforeSha)
      transferLine(patches, lineIndex, false).exactLocation()?.let {
        Side.LEFT to it
      }
    }
    else if (fileHistory.compare(fromCommitSha, afterSha) < 0) {
      val patches = fileHistory.getPatchesBetween(fromCommitSha, afterSha)
      transferLine(patches, lineIndex, false).exactLocation()?.let {
        Side.RIGHT to it
      }
    }
    else {
      error("Couldn't find commit ${fromCommitSha}")
    }

  private fun transferLine(patchChain: List<TextFilePatch>, lineIndex: Int, rightToLeft: Boolean): TransferResult {
    var currentLine = lineIndex.toDouble()
    var isEstimate = false

    val patches = if (rightToLeft) patchChain.asReversed() else patchChain

    for (patch in patches) {
      val changeOnlyRanges = patch.hunks.map { hunk ->
        val ranges = PatchHunkUtil.getChangeOnlyRanges(hunk)
        if (rightToLeft) ranges.map { reverseRange(it) } else ranges
      }.flatten()

      var offset = 0.0
      loop@ for (range in changeOnlyRanges) {
        when {
          currentLine < range.start1 ->
            break@loop
          range.start1 <= currentLine && currentLine < range.end1 -> {
            isEstimate = true
            // Careful when changing: we assume that range.end1 - range.start1 is non-zero due to the above check
            // Estimated position algo: find the relative position of the line in the hunk, then map it to that relative position in the new hunk
            // A choice can be made about how to map to 'relative position' though; we choose to find the relative position based on the 'center' of the line within a range:
            //  - in a half-open range [1, 2), line 1 has relative position 0.5 (the 'center' of the line is used rather than the start)
            //  - in a half-open range [1, 3), line 1 has relative position 0.333, line 2 has relative position 0.667
            val relativePosition = ((currentLine - range.start1) + 1) / ((range.end1 - range.start1) + 1).toDouble()
            offset -= currentLine - range.start1 // move line to the start of the range in the old hunk
            // We map relative positions back to the output line in the following way:
            //  - for any relPos and out-range [1, 1) - there no output line -, we map to line 1
            //  - for any relPos and out-range [1, 2) - there is only 1 possible output line -, we map to line 1.5 (and round after all mapping is done)
            //  - for relPos = 0.5 and out-range [1, 3) - there are 2 possible output lines -, we map to line 2
            //  - for relPos = 0.5 and out-range [1, 4) - there are 3 possible output lines -, we map to line 2.5 (and round after all mapping is done)
            offset += relativePosition * (range.end2 - range.start2) // then to the relative position in the new hunk
          }
          range.end1 <= currentLine ->
            offset += (range.end2 - range.start2) - (range.end1 - range.start1)
        }
      }
      currentLine += offset
    }

    // Note that the only way for the line number to become floating point is when it IS an estimate
    return when (isEstimate) {
      false -> TransferResult.ExactTransfer(floor(currentLine).toInt())
      true -> TransferResult.EstimatedTransfer(floor(currentLine).toInt())
    }
  }

  private fun reverseRange(range: Range) = Range(range.start2, range.end2, range.start1, range.end1)

  private sealed interface TransferResult {
    data class ExactTransfer(override val line: Int) : TransferResult
    data class EstimatedTransfer(override val line: Int) : TransferResult

    val line: Int?

    fun exactLocation(): Int? = (this as? ExactTransfer)?.line
  }

  companion object {
    private val LOG = logger<GitTextFilePatchWithHistory>()
  }
}