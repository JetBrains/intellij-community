// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger.sourcemap

/**
 * Simple wrapper around [CharSequence] that allows to create subsequences without expensive string copying
 */
private class StringViewCharSequence private constructor(val sourceSequence: CharSequence, val start: Int, val end: Int) : CharSequence {
  init {
    require(start in 0..end && end in 0..sourceSequence.length) {
      "Invalid sequence range: start=$start, end=$end, length=${sourceSequence.length}"
    }
  }

  constructor(sourceSequence: CharSequence) : this(sourceSequence, 0, sourceSequence.length)

  override val length: Int
    get() = end - start

  override fun get(index: Int): Char = sourceSequence[start + index]

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
    if (startIndex == 0 && endIndex == length) {
      return this
    }
    return StringViewCharSequence(sourceSequence, start + startIndex, start + endIndex)
  }

  override fun toString(): String = sourceSequence.substring(start, end)
}

// 8 Kilobytes, but since strings in Java are encoded in UTF-16 and source maps are UTF-8, then it is actually 4Kb
private const val STRING_VIEW_THRESHOLD = 0x2000

internal fun wrapWithStringViewIfNeeded(sourceSequence: CharSequence): CharSequence {
  if (sourceSequence.length < STRING_VIEW_THRESHOLD) {
    return sourceSequence
  }
  return StringViewCharSequence(sourceSequence)
}