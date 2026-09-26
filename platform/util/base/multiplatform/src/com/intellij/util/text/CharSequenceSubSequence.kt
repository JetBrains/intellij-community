// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text

import com.intellij.openapi.util.text.CharSequenceWithStringHash
import com.intellij.openapi.util.text.stringHashCode
import com.intellij.util.text.CharArrayUtilKmp.fromSequence
import com.intellij.util.text.CharArrayUtilKmp.getChars
import kotlin.jvm.Transient

/**
 * `CharSequenceSubSequence` allows to save time and memory in text processing code. It avoids
 * creation of a new char array on every `subSequence(int, int)` call in contrast to [String.subSequence],
 * which actually creates a new [String] instance every time it's invoked.
 *
 *
 * The downside of using `CharSequenceSubSequence` is that it keeps reference to the original sequence, which may be large.
 * Therefore, results of text processing should always be stored as [String], to allow garbage collection of the original sequence.
 *
 *
 * `CharSequenceSubSequence` implements `hashCode` and `equals` in such a way that it can be compared against [String] map keys
 * and set elements without creating a [String] instance. However, `CharSequenceSubSequence` should not be used
 * as a map key or set element, since it keeps reference to the original sequence and prevents its collection.
 */
open class CharSequenceSubSequence(
  val baseSequence: CharSequence,
  private val start: Int,
  private val end: Int,
) : CharSequence, CharArrayExternalizable, CharSequenceWithStringHash {

  constructor(baseSequence: CharSequence) : this(baseSequence, 0, baseSequence.length)

  @Transient
  private var hash = 0

  init {
    if (start < 0 || end > baseSequence.length || start > end) {
      throw IndexOutOfBoundsException("chars sequence.length:" + baseSequence.length +
                                      ", start:" + start +
                                      ", end:" + end)
    }
  }

  override val length: Int
    get() = end - start

  override fun get(index: Int): Char =
    baseSequence[index + start]

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
    if (startIndex == this.start && endIndex == this.end) return this
    return CharSequenceSubSequence(this.baseSequence, this.start + startIndex, this.start + endIndex)
  }

  override fun toString(): String {
    if (this.baseSequence is String) {
      return this.baseSequence.substring(start, end)
    }
    return baseSequence.fromSequence(start, end).concatToString()
  }

  override fun getChars(start: Int, end: Int, dest: CharArray, destPos: Int) {
    require(end - start <= this.end - this.start)
    baseSequence.getChars(dest, start + this.start, destPos, end - start)
  }

  override fun hashCode(): Int {
    var h = hash
    if (h == 0) {
      h = baseSequence.stringHashCode(start, end)
      hash = h
    }
    return h
  }

  override fun equals(other: Any?): Boolean {
    if (other === this) return true
    return other is CharSequence && this.contentEquals(other)
  }
}
