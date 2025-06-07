// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.builder

/**
 * Allows saving time and memory in text processing code. It avoids creation of a new char array on every [CharSequence.subSequence] call
 * in contrast to [String.subSequence], which actually creates a new [String] instance every time it's invoked.
 *
 * Is heavily inspired by com.intellij.util.text.CharSequenceSubSequence
 */
internal class CharSequenceSubSequence(chars: CharSequence, start: Int, end: Int) : CharSequence {
  private val chars: CharSequence
  private val start: Int
  private val end: Int

  init {
    if (start < 0 || end > chars.length || start > end) {
      throw IndexOutOfBoundsException("chars sequence.length:${chars.length}, start:$start, end:$end")
    }
    this.chars = chars
    this.start = start
    this.end = end
  }

  override val length: Int
    get() = end - start

  override fun get(index: Int): Char =
    chars[start + index]

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
    if (startIndex == start && endIndex == end) return this
    return CharSequenceSubSequence(chars, start + startIndex, start + endIndex)
  }

  override fun toString(): String =
    throw UnsupportedOperationException("Not supported yet") // TODO?
}