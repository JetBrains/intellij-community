// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.NonNls

class MockMultiLineImmutableDocument(
  private val text: String,
) : Document {
  private val lines = text.split('\n')

  override fun getImmutableCharSequence(): @NlsSafe CharSequence {
    return text
  }

  override fun getLineCount(): Int {
    return lines.size
  }

  override fun getLineNumber(offset: Int): Int {
    var currentOffset = 0
    for (lineIndex in lines.indices) {
      val lineLength = lines[lineIndex].length
      if (offset <= currentOffset + lineLength) {
        return lineIndex
      }
      currentOffset += lineLength + 1 // +1 for newline character
    }
    return lines.size - 1
  }

  override fun getLineStartOffset(line: Int): Int {
    checkLineIndex(line)
    var offset = 0
    for (i in 0 until line) {
      offset += lines[i].length + 1 // +1 for newline character
    }
    return offset
  }

  override fun getLineEndOffset(line: Int): Int {
    checkLineIndex(line)
    return getLineStartOffset(line) + lines[line].length
  }


  override fun insertString(offset: Int, s: @NonNls CharSequence) {
    readOnlyException()
  }

  override fun deleteString(startOffset: Int, endOffset: Int) {
    readOnlyException()
  }

  override fun replaceString(startOffset: Int, endOffset: Int, s: @NlsSafe CharSequence) {
    readOnlyException()
  }

  override fun isWritable(): Boolean {
    return false
  }

  override fun getModificationStamp(): Long {
    return 0
  }

  override fun createRangeMarker(
    startOffset: Int,
    endOffset: Int,
    surviveOnExternalChange: Boolean,
  ): RangeMarker {
    throw UnsupportedOperationException("RangeMarkers not supported in ${this::class.simpleName}")
  }

  override fun createGuardedBlock(startOffset: Int, endOffset: Int): RangeMarker {
    throw UnsupportedOperationException("Guarded blocks not supported in ${this::class.simpleName}")
  }

  override fun setText(text: CharSequence) {
    throw UnsupportedOperationException("${this::class.simpleName} is read-only")
  }

  override fun <T : Any?> getUserData(key: Key<T?>): T? {
    return null
  }

  override fun <T : Any?> putUserData(key: Key<T?>, value: T?) {
    // No-op - user data not supported
  }

  private fun readOnlyException(): Nothing {
    throw UnsupportedOperationException("${this::class.simpleName} is read-only")
  }


  private fun checkLineIndex(index: Int) {
    if (index < 0 || index >= lineCount) {
      throw IndexOutOfBoundsException("Wrong line: " + index + ". Available lines count: " + getLineCount())
    }
  }
}