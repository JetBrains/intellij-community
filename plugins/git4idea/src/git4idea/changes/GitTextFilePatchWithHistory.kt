// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.changes

import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.patch.PatchHunkUtil
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Holds the [patch] between two commits in [fileHistory]
 *
 * @param isCumulative [true] if [patch] is between start and end of [fileHistory] which spans from merge base to last commit,
 *                     [false] if it is a patch between a pair of adjacent commits in [fileHistory]
 */
@Internal
class GitTextFilePatchWithHistory(val patch: TextFilePatch, val isCumulative: Boolean, val fileHistory: GitFileHistory) {

  /**
   * Check that history contains the specified file at the [commitSha]
   * File may have not been changed in this particular commit, but was changed before or after
   */
  fun contains(commitSha: String, filePath: String): Boolean = fileHistory.contains(commitSha, filePath)

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

    fun tryTransferToParent(): DiffLineLocation? {
      return if (fileHistory.compare(afterSha, fromCommitSha) < 0) {
        val patches = fileHistory.getPatchesBetween(afterSha, fromCommitSha)
        transferLine(patches, lineIndex, true)
      }
      else if (fileHistory.compare(beforeSha, fromCommitSha) < 0) {
        val patches = fileHistory.getPatchesBetween(beforeSha, fromCommitSha)
        transferLine(patches, lineIndex, true)
      }
      else {
        null
      }
    }

    fun tryTransferToChild(): DiffLineLocation? {
      return if (fileHistory.compare(fromCommitSha, beforeSha) < 0) {
        val patches = fileHistory.getPatchesBetween(fromCommitSha, beforeSha)
        transferLine(patches, lineIndex, false)
      }
      else if (fileHistory.compare(fromCommitSha, afterSha) < 0) {
        val patches = fileHistory.getPatchesBetween(fromCommitSha, afterSha)
        transferLine(patches, lineIndex, false)
      }
      else {
        null
      }
    }

    return try {
      when (bias) {
        Side.LEFT -> tryTransferToParent() ?: tryTransferToChild()
        Side.RIGHT -> tryTransferToChild() ?: tryTransferToParent()
      }
    }
    catch (e: Exception) {
      LOG.debug(e)
      null
    }
  }

  private fun transferLine(patchChain: List<TextFilePatch>, lineIndex: Int, rightToLeft: Boolean): DiffLineLocation? {
    val side: Side = if (rightToLeft) Side.RIGHT else Side.LEFT
    var currentLine: Int = lineIndex

    val patches = if (rightToLeft) patchChain.asReversed() else patchChain

    for (patch in patches) {
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
    return DiffLineLocation(side, currentLine)
  }

  private fun reverseRange(range: Range) = Range(range.start2, range.end2, range.start1, range.end1)

  companion object {
    private val LOG = logger<GitTextFilePatchWithHistory>()
  }
}