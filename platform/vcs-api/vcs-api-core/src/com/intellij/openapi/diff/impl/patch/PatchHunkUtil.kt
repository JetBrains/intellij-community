// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch

import com.intellij.diff.util.LineRange
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
    var start1 = hunk.startLineBefore.coerceAtLeast(0)
    var start2 = hunk.startLineAfter.coerceAtLeast(0)
    var end1 = hunk.startLineBefore.coerceAtLeast(0)
    var end2 = hunk.startLineAfter.coerceAtLeast(0)
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

  /**
   * @param diffFile if [true] will return index of the line in diff file (with header and no-newline comments), else - in [PatchHunk]
   */
  fun findHunkLineIndex(hunk: PatchHunk, locationInDiff: Pair<Side, Int>, diffFile: Boolean = false): Int? {
    val (side, fileLineIndex) = locationInDiff
    val sideFileLineIndex = fileLineIndex - side.select(hunk.startLineBefore, hunk.startLineAfter)
    var sideFileLineCounter = 0

    // +1 for header
    var hunkLineIndex = if (diffFile) 1 else 0

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
      hunkLineIndex += if (diffFile && line.isSuppressNewLine) 2 else 1
    }
    return lastMatchedLineWithNewline
  }

  fun findDiffFileLineIndex(patch: TextFilePatch, locationInDiff: Pair<Side, Int>): Int? {
    val (hunk, offset) = findHunkWithOffset(patch, locationInDiff) ?: return null
    val hunkLineIndex = findHunkLineIndex(hunk, locationInDiff, true) ?: return null

    // header included in offset
    return offset + (hunkLineIndex - 1)
  }

  private fun findHunkWithOffset(patch: TextFilePatch, locationInDiff: Pair<Side, Int>): Pair<PatchHunk, Int>? {
    val (side, lineIndex) = locationInDiff
    var diffLineCounter = 0
    for (hunk in patch.hunks) {
      // +1 for header
      diffLineCounter++
      val range = getRange(hunk)
      val start = side.select(range.start1, range.start2)
      val end = side.select(range.end1, range.end2)

      if (lineIndex in start..end) {
        return hunk to diffLineCounter
      }

      val hunkLinesCount = hunk.lines.size + hunk.lines.count { it.isSuppressNewLine }
      diffLineCounter += hunkLinesCount
    }
    return null
  }

  fun createPatchFromHunk(filePath: String, diffHunk: String): String {
    return """--- a/$filePath
+++ b/$filePath
""" + diffHunk
  }

  fun truncateHunkBefore(hunk: PatchHunk, hunkLineIndex: Int): PatchHunk {
    if (hunkLineIndex <= 0) return hunk
    val lines = hunk.lines

    var startLineBefore: Int = hunk.startLineBefore
    var startLineAfter: Int = hunk.startLineAfter

    for (i in 0 until hunkLineIndex) {
      val line = lines[i]
      when (line.type) {
        PatchLine.Type.CONTEXT -> {
          startLineBefore++
          startLineAfter++
        }
        PatchLine.Type.ADD -> startLineAfter++
        PatchLine.Type.REMOVE -> startLineBefore++
      }
    }
    val truncatedLines = lines.subList(hunkLineIndex, lines.size)
    return PatchHunk(startLineBefore, hunk.endLineBefore, startLineAfter, hunk.endLineAfter).apply {
      for (line in truncatedLines) {
        addLine(line)
      }
    }
  }

  fun truncateHunkAfter(hunk: PatchHunk, hunkLineIndex: Int): PatchHunk {
    val lines = hunk.lines
    if (hunkLineIndex > lines.size - 1) return hunk

    var endLineBefore: Int = hunk.endLineBefore
    var endLineAfter: Int = hunk.endLineAfter

    for (i in lines.size - 1 downTo hunkLineIndex) {
      val line = lines[i]
      when (line.type) {
        PatchLine.Type.CONTEXT -> {
          endLineBefore--
          endLineAfter--
        }
        PatchLine.Type.ADD -> endLineAfter--
        PatchLine.Type.REMOVE -> endLineBefore--
      }
    }
    val truncatedLines = lines.subList(0, hunkLineIndex + 1)
    return PatchHunk(hunk.startLineBefore, endLineBefore, hunk.startLineAfter, endLineAfter).apply {
      for (line in truncatedLines) {
        addLine(line)
      }
    }
  }

  fun getLinesInRange(hunk: PatchHunk, side: Side, range: LineRange): List<PatchLine> {
    var lineIdx = hunk.startLineAfter
    val result = mutableListOf<PatchLine>()
    for (line in hunk.lines) {
      val ignoredType = if (side == Side.RIGHT) PatchLine.Type.REMOVE else PatchLine.Type.ADD
      if (line.type == ignoredType) {
        continue
      }
      if (lineIdx >= range.start) {
        result.add(line)
      }
      lineIdx++
      if (lineIdx >= range.end) {
        break
      }
    }
    return result
  }
}

fun Collection<PatchHunk>.withoutContext(): Sequence<Range> =
  asSequence().map { PatchHunkUtil.getChangeOnlyRanges(it) }.flatten()