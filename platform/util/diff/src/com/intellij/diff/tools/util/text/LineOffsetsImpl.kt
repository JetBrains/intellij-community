// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.text

import com.intellij.util.diff.binarySearch
import com.intellij.util.fastutil.ints.IntArrayList
import com.intellij.util.fastutil.ints.toArray
import kotlin.jvm.JvmStatic

class LineOffsetsImpl private constructor(private val myLineEnds: IntArray, override val textLength: Int) : LineOffsets {
  override fun getLineStart(line: Int): Int {
    checkLineIndex(line)
    if (line == 0) return 0
    return myLineEnds[line - 1] + 1
  }

  override fun getLineEnd(line: Int): Int {
    checkLineIndex(line)
    return myLineEnds[line]
  }

  override fun getLineEnd(line: Int, includeNewline: Boolean): Int {
    checkLineIndex(line)
    return myLineEnds[line] + (if (includeNewline && line != myLineEnds.size - 1) 1 else 0)
  }

  override fun getLineNumber(offset: Int): Int {
    if (offset < 0 || offset > textLength) {
      throw IndexOutOfBoundsException("Wrong offset: $offset. Available text length: $textLength")
    }
    if (offset == 0) return 0
    if (offset == textLength) return lineCount - 1

    val bsResult = myLineEnds.binarySearch(offset)
    return if (bsResult >= 0) bsResult else -bsResult - 1
  }

  override val lineCount: Int
    get() = myLineEnds.size

  private fun checkLineIndex(index: Int) {
    if (index < 0 || index >= lineCount) {
      throw IndexOutOfBoundsException("Wrong line: $index. Available lines count: $lineCount")
    }
  }

  companion object {
    @JvmStatic
    fun create(text: CharSequence): LineOffsets {
      val ends = IntArrayList()

      var index = 0
      while (true) {
        val lineEnd = text.indexOf('\n', index)
        if (lineEnd != -1) {
          ends.add(lineEnd)
          index = lineEnd + 1
        }
        else {
          ends.add(text.length)
          break
        }
      }

      return LineOffsetsImpl(ends.toArray(), text.length)
    }
  }
}
