// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.diff.util.Side
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.TextFilePatch
import org.jetbrains.plugins.github.util.GHPatchHunkUtil

class GHPRChangedFileLinesMapperImpl(private val diff: TextFilePatch) : GHPRChangedFileLinesMapper {

  override fun findDiffLine(side: Side, fileLineIndex: Int): Int? {
    val (hunk, offset) = findHunkWithOffset(side, fileLineIndex) ?: return null
    val hunkLineIndex = findHunkLineIndexFromFileSideLineIndex(hunk, side, fileLineIndex) ?: return null

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

      val hunkLinesCount = getHunkLinesCount(hunk)
      diffLineCounter += hunkLinesCount
    }
    return null
  }

  private fun findHunkLineIndexFromFileSideLineIndex(hunk: PatchHunk, side: Side, fileLineIndex: Int): Int? {
    val sideFileLineIndex = fileLineIndex - side.select(hunk.startLineBefore, hunk.startLineAfter)
    var sideFileLineCounter = 0

    // +1 for header
    var hunkLineIndex = 1

    var lastMatchedLineWithNewline: Int? = null
    for (line in hunk.lines) {
      if (line.type == PatchLine.Type.ADD && side == Side.RIGHT ||
          line.type == PatchLine.Type.REMOVE && side == Side.LEFT ||
          line.type == PatchLine.Type.CONTEXT) {
        if (sideFileLineCounter == sideFileLineIndex) return hunkLineIndex
        sideFileLineCounter++
        //potentially a comment on a newline
        if (sideFileLineCounter == sideFileLineIndex && !line.isSuppressNewLine) lastMatchedLineWithNewline = hunkLineIndex
      }
      hunkLineIndex += if (line.isSuppressNewLine) 2 else 1
    }
    return lastMatchedLineWithNewline
  }

  override fun findFileLocation(diffLineIndex: Int): Pair<Side, Int>? {
    val (hunk, offset) = findHunkWithOffset(diffLineIndex) ?: return null

    val hunkLineIndex = diffLineIndex - offset
    if (hunkLineIndex == 0) return null

    return findSideFileLineFromHunkLineIndex(hunk, hunkLineIndex)
  }

  private fun findHunkWithOffset(diffLineIndex: Int): Pair<PatchHunk, Int>? {
    var diffLineCounter = 0
    for (hunk in diff.hunks) {
      val hunkLinesCount = getHunkLinesCount(hunk)
      diffLineCounter += hunkLinesCount

      if (diffLineIndex < diffLineCounter) {
        return hunk to (diffLineCounter - hunkLinesCount)
      }
    }
    return null
  }

  private fun findSideFileLineFromHunkLineIndex(hunk: PatchHunk, hunkLineIndex: Int): Pair<Side, Int>? {
    //+1 for header
    var hunkLineIterator = 1

    var lineNumberLeft = hunk.startLineBefore
    var lineNumberRight = hunk.startLineAfter

    for (line in hunk.lines) {
      if (hunkLineIterator == hunkLineIndex) {
        return when (line.type) {
          PatchLine.Type.REMOVE -> Side.LEFT to lineNumberLeft
          PatchLine.Type.CONTEXT, PatchLine.Type.ADD -> Side.RIGHT to lineNumberRight
        }
      }

      when (line.type) {
        PatchLine.Type.REMOVE -> lineNumberLeft++
        PatchLine.Type.ADD -> lineNumberRight++
        PatchLine.Type.CONTEXT -> {
          lineNumberLeft++
          lineNumberRight++
        }
      }

      hunkLineIterator += if (!line.isSuppressNewLine) 1 else 2
      //can't show comments on \No newline
      if (hunkLineIterator > hunkLineIndex) return null
    }
    return null
  }

  // +1 for header
  private fun getHunkLinesCount(hunk: PatchHunk) = hunk.lines.size + hunk.lines.count { it.isSuppressNewLine } + 1
}