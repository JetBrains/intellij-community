// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch

import com.intellij.diff.util.Range
import com.intellij.diff.util.Side

object PatchHunkUtil {

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

  fun findHunkLineIndex(hunk: PatchHunk, locationInDiff: Pair<Side, Int>): Int? {
    val (side, fileLineIndex) = locationInDiff
    val sideFileLineIndex = fileLineIndex - side.select(hunk.startLineBefore, hunk.startLineAfter)

    var sideFileLineCounter = 0

    var lastMatchedLineWithNewline: Int? = null
    for ((index, line) in hunk.lines.withIndex()) {
      if (line.type == PatchLine.Type.ADD && side == Side.RIGHT ||
          line.type == PatchLine.Type.REMOVE && side == Side.LEFT ||
          line.type == PatchLine.Type.CONTEXT) {
        if (sideFileLineCounter == sideFileLineIndex) return index
        sideFileLineCounter++
        //potentially a comment on a newline
        if (sideFileLineCounter == sideFileLineIndex && !line.isSuppressNewLine) lastMatchedLineWithNewline = index
      }
    }
    return lastMatchedLineWithNewline
  }

  fun createPatchFromHunk(filePath: String, diffHunk: String): String {
    return """--- a/$filePath
+++ b/$filePath
""" + diffHunk
  }
}