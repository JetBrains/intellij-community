// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.diff.util.Side
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import org.jetbrains.plugins.github.util.GHPatchHunkUtil

class GHPRChangedFileLinesMapperImpl(private val diff: TextFilePatch) : GHPRChangedFileLinesMapper {

  override fun findDiffLine(side: Side, fileLineIndex: Int): Int? {
    val (hunk, offset) = findHunkWithOffset(side, fileLineIndex) ?: return null
    val hunkLineIndex = GHPatchHunkUtil.findHunkLineIndexFromFileSideLineIndex(hunk, side, fileLineIndex) ?: return null

    return offset + hunkLineIndex
  }

  private fun findHunkWithOffset(side: Side, fileLineIndex: Int): Pair<PatchHunk, Int>? {
    var diffLineCounter = 0
    for (hunk in diff.hunks) {
      val range = GHPatchHunkUtil.getRange(hunk)
      val start = side.select(range.start1, range.start2)
      val end = side.select(range.end1, range.end2)

      if (fileLineIndex in start until end) {
        return hunk to diffLineCounter
      }

      val hunkLinesCount = GHPatchHunkUtil.getHunkLinesCount(hunk)
      diffLineCounter += hunkLinesCount
    }
    return null
  }

  override fun findFileLocation(diffLineIndex: Int): Pair<Side, Int>? {
    val (hunk, offset) = findHunkWithOffset(diffLineIndex) ?: return null

    val hunkLineIndex = diffLineIndex - offset
    if (hunkLineIndex == 0) return null

    return GHPatchHunkUtil.findSideFileLineFromHunkLineIndex(hunk, hunkLineIndex)
  }

  private fun findHunkWithOffset(diffLineIndex: Int): Pair<PatchHunk, Int>? {
    var diffLineCounter = 0
    for (hunk in diff.hunks) {
      val hunkLinesCount = GHPatchHunkUtil.getHunkLinesCount(hunk)
      diffLineCounter += hunkLinesCount

      if (diffLineIndex < diffLineCounter) {
        return hunk to (diffLineCounter - hunkLinesCount)
      }
    }
    return null
  }
}