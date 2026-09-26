// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util

import com.intellij.diff.tools.util.text.LineOffsets
import org.jetbrains.annotations.NonNls
import kotlin.jvm.JvmStatic

object DiffRangeUtil {
  @JvmStatic
  fun getLinesContent(sequence: CharSequence, lineOffsets: LineOffsets, line1: Int, line2: Int): CharSequence {
    return getLinesContent(sequence, lineOffsets, line1, line2, false)
  }

  @JvmStatic
  fun getLinesContent(
    sequence: CharSequence,
    lineOffsets: LineOffsets,
    line1: Int,
    line2: Int,
    includeNewline: Boolean
  ): CharSequence {
    check(sequence.length == lineOffsets.textLength)
    val linesRange = getLinesRange(lineOffsets, line1, line2, includeNewline)
    return sequence.subSequence(linesRange.startOffset, linesRange.endOffset)
  }

  @JvmStatic
  fun getLinesRange(lineOffsets: LineOffsets, line1: Int, line2: Int, includeNewline: Boolean): LinesRange {
    return if (line1 == line2) {
      val lineStartOffset = if (line1 < lineOffsets.lineCount) lineOffsets.getLineStart(line1) else lineOffsets.textLength
      LinesRange(lineStartOffset, lineStartOffset)
    }
    else {
      val startOffset = lineOffsets.getLineStart(line1)
      var endOffset = lineOffsets.getLineEnd(line2 - 1)
      if (includeNewline && endOffset < lineOffsets.textLength) endOffset++
      LinesRange(startOffset, endOffset)
    }
  }

  @JvmStatic
  fun getLines(text: CharSequence, lineOffsets: @NonNls LineOffsets): MutableList<String> {
    return getLines(text, lineOffsets, 0, lineOffsets.lineCount)
  }

  @JvmStatic
  fun getLines(text: CharSequence, lineOffsets: @NonNls LineOffsets, startLine: Int, endLine: Int): MutableList<String> {
    if (startLine < 0 || startLine > endLine || endLine > lineOffsets.lineCount) {
      throw IndexOutOfBoundsException("Wrong line range: [$startLine, $endLine); lineCount: '${lineOffsets.lineCount}'")
    }

    val result: MutableList<String> = ArrayList()
    for (i in startLine..<endLine) {
      val start = lineOffsets.getLineStart(i)
      val end = lineOffsets.getLineEnd(i)
      result.add(text.subSequence(start, end).toString())
    }
    return result
  }
}

class LinesRange(val startOffset: Int, val endOffset: Int)