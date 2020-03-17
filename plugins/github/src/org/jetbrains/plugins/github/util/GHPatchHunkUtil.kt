// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchLine

object GHPatchHunkUtil {

  fun getRange(hunk: PatchHunk): Range {
    var end1 = hunk.startLineBefore
    var end2 = hunk.startLineAfter

    for (line in hunk.lines) {
      when (line.type) {
        PatchLine.Type.REMOVE -> {
          end1++
        }
        PatchLine.Type.ADD -> {
          end2++
        }
        PatchLine.Type.CONTEXT -> {
          end1++
          end2++
        }
      }
    }

    return Range(hunk.startLineBefore, end1, hunk.startLineAfter, end2)
  }

  fun getChangeOnlyRanges(hunk: PatchHunk): List<Range> {
    val ranges = mutableListOf<Range>()
    var start1 = hunk.startLineBefore
    var start2 = hunk.startLineAfter
    var end1 = hunk.startLineBefore
    var end2 = hunk.startLineAfter
    var changeFound = false
    var newLine1 = false
    var newLine2 = false

    for (line in hunk.lines) {
      when (line.type) {
        PatchLine.Type.REMOVE -> {
          end1++
          changeFound = true
          newLine1 = !line.isSuppressNewLine
        }
        PatchLine.Type.ADD -> {
          end2++
          changeFound = true
          newLine2 = !line.isSuppressNewLine
        }
        PatchLine.Type.CONTEXT -> {
          if (changeFound) {
            ranges.add(Range(start1, end1, start2, end2))
            start1 = end1
            start2 = end2
          }
          start1++
          start2++
          end1++
          end2++
          changeFound = false
          newLine1 = !line.isSuppressNewLine
          newLine2 = !line.isSuppressNewLine
        }
      }
    }
    if (changeFound) {
      if (newLine1 != newLine2) {
        if (newLine1) end1++
        if (newLine2) end2++
      }
      ranges.add(Range(start1, end1, start2, end2))
    }
    return ranges
  }

  fun createPatchFromHunk(filePath: String, diffHunk: String): String {
    return """--- a/$filePath
+++ b/$filePath
""" + diffHunk
  }

  fun findHunkLineIndexFromFileSideLineIndex(hunk: PatchHunk, side: Side, fileLineIndex: Int): Int? {
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

  fun findSideFileLineFromHunkLineIndex(hunk: PatchHunk, hunkLineIndex: Int): Pair<Side, Int>? {
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
  fun getHunkLinesCount(hunk: PatchHunk) = hunk.lines.size + hunk.lines.count { it.isSuppressNewLine } + 1
}