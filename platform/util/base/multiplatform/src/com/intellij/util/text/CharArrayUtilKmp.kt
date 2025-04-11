// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text

import fleet.util.multiplatform.linkToActual
import org.jetbrains.annotations.ApiStatus
import kotlin.math.min
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmOverloads

@ApiStatus.Experimental
object CharArrayUtilKmp {
  @JvmStatic
  fun fromSequenceWithoutCopying(seq: CharSequence?): CharArray? {
    if (seq is CharSequenceBackedByArray) {
      return seq.chars
    }

    return fromSequenceWithoutCopyingPlatformSpecific(seq)
  }

  @JvmStatic
  @JvmOverloads
  fun containLineBreaks(
    seq: CharSequence?,
    fromOffset: Int = 0,
    endOffset: Int = seq?.length ?: 0,
  ): Boolean {
    return seq != null && (fromOffset..<endOffset).any { i ->
      val c = seq[i]
      c == '\n' || c == '\r'
    }
  }

  /**
   * @return the underlying char[] array if any, or the new chara array if not
   */
  @JvmStatic
  fun fromSequence(seq: CharSequence): CharArray {
    val underlying: CharArray? = fromSequenceWithoutCopying(seq)
    return underlying?.copyOf() ?: seq.fromSequence(0, seq.length)
  }

  /**
   * @return a new char array containing the subsequence's chars
   */
  @JvmStatic
  fun CharSequence.fromSequence(start: Int, end: Int): CharArray {
    val result = CharArray(end - start)
    getChars(result, start, 0, end - start)
    return result
  }

  /**
   * Copies the necessary number of symbols from the given char sequence to the given array.
   *
   * @param this@getChars         source data holder
   * @param dst         output data buffer
   * @param srcOffset   source text offset
   * @param dstOffset   start offset to use within the given output data buffer
   * @param len         number of source data symbols to copy to the given buffer
   */
  @JvmOverloads
  @JvmStatic
  fun CharSequence.getChars(dst: CharArray, srcOffset: Int = 0, dstOffset: Int, len: Int = this.length - srcOffset) {
    if (this is CharArrayExternalizable) {
      this.getChars(srcOffset, srcOffset + len, dst, dstOffset)
      return
    }

    if (len >= GET_CHARS_THRESHOLD) {
      if (this is String) {
        this.toCharArray(dst, dstOffset, srcOffset, srcOffset + len)
        return
      }
      if (this is CharSequenceBackedByArray) {
        (subSequence(srcOffset, srcOffset + len) as CharSequenceBackedByArray).getChars(dst, dstOffset)
        return
      }
      if (this is StringBuilder) {
        this.toCharArray(dst, dstOffset, srcOffset, srcOffset + len)
        return
      }

      if (getCharsPlatformSpecific(this, srcOffset, dst, dstOffset, len)) {
        return
      }
    }

    var i = 0
    var j = srcOffset
    val max = srcOffset + len
    while (j < max && i < dst.size) {
      dst[i + dstOffset] = this[j]
      i++
      j++
    }
  }

  /**
   * Tries to find an offset from the `[startOffset; endOffset)` interval such that a char from the given buffer is
   * not contained at the given 'chars' string.
   *
   *
   * Example:
   * `buffer="abc", startOffset=0, endOffset = 3, chars="ab". Result: 2`
   *
   * @param this         target buffer which symbols should be checked
   * @param startOffset  start offset to use within the given buffer (inclusive)
   * @param endOffset    end offset to use within the given buffer (exclusive)
   * @param chars        pass-through symbols
   * @return             offset from the `[startOffset; endOffset)` which points to a symbol at the given buffer such
   * as that that symbol is not contained at the given 'chars';
   * `endOffset` otherwise
   */
  @JvmOverloads
  @JvmStatic
  fun CharSequence.shiftForward(chars: String, startOffset: Int, endOffset: Int = this.length): Int {
    var offset = startOffset
    val limit = min(endOffset, length)
    while (offset < limit) {
      val c = this[offset]
      var i = 0
      while (i < chars.length) {
        if (c == chars[i]) break
        i++
      }
      if (i >= chars.length) {
        return offset
      }
      offset++
    }
    return endOffset
  }

  @JvmStatic
  fun CharArray.shiftBackward(offset: Int, chars: String): Int {
    return CharArrayCharSequence(*this).shiftBackward(offset, chars)
  }

  @JvmStatic
  fun CharSequence.shiftBackward(offset: Int, chars: String): Int {
    return shiftBackward(0, offset, chars)
  }

  /**
   * @return minimal offset in the `minOffset`-`maxOffset`  range after which `buffer` contains only characters from
   * `chars` in the range
   */
  @JvmStatic
  fun CharSequence.shiftBackward(minOffset: Int, maxOffset: Int, chars: String): Int {
    if (maxOffset >= length) return maxOffset

    var offset = maxOffset
    while (true) {
      if (offset < minOffset) break
      val c = this[offset]
      var i = 0
      while (i < chars.length) {
        if (c == chars[i]) break
        i++
      }
      if (i == chars.length) break
      offset--
    }
    return offset
  }

  @JvmStatic
  fun CharSequence.shiftForwardUntil(offset: Int, chars: String): Int {
    var offset = offset
    while (true) {
      if (offset >= length) break
      val c = this[offset]
      var i = 0
      while (i < chars.length) {
        if (c == chars[i]) break
        i++
      }
      if (i < chars.length) break
      offset++
    }
    return offset
  }

  /**
   * Calculates offset that points to the given buffer and has the following characteristics:
   *
   *
   *
   *  * is less than or equal to the given offset;
   *  *
   * it's guaranteed that all symbols of the given buffer that are located at `(returned offset; given offset]`
   * interval differ from the given symbols;
   *
   *
   *
   *
   * Example: suppose that this method is called with buffer that holds `'test data'` symbols, offset that points
   * to the last symbols and `'sf'` as a chars to exclude. Offset that points to `'s'` symbol
   * is returned then, i.e., all symbols of the given buffer that are located after it and not after given offset
   * (`'t data'`) are guaranteed to not contain given chars (`'sf'`).
   *
   * @param this@shiftBackwardUntil      symbols buffer to check
   * @param offset      initial symbols buffer offset to use
   * @param chars       chars to exclude
   * @return            offset of the given buffer that guarantees that all symbols at `(returned offset; given offset]`
   * interval of the given buffer differ from symbols of given `'chars'` arguments;
   * given offset is returned if it is outside of given buffer bounds;
   * `'-1'` is returned if all document symbols that precede given offset differ from symbols
   * of the given `'chars to exclude'`
   */
  @JvmStatic
  fun CharSequence.shiftBackwardUntil(offset: Int, chars: String): Int {
    var offset = offset
    if (offset >= length) return offset
    while (true) {
      if (offset < 0) break
      val c = this[offset]
      var i = 0
      while (i < chars.length) {
        if (c == chars[i]) break
        i++
      }
      if (i < chars.length) break
      offset--
    }
    return offset
  }

  @JvmStatic
  fun CharArray.regionMatches(start: Int, end: Int, s: CharSequence): Boolean {
    val len = s.length
    if (start + len > end) return false
    if (start < 0) return false
    for (i in 0..<len) {
      if (this[start + i] != s[i]) return false
    }
    return true
  }

  @JvmStatic
  fun CharSequence.regionMatches(offset: Int, s: CharSequence): Boolean {
    if (offset < 0 || offset + s.length > length) return false
    return (0..<s.length).all { i -> this[offset + i] == s[i] }
  }

  @JvmStatic
  fun CharSequence.regionMatches(start: Int, end: Int, s: CharSequence): Boolean {
    val len = s.length
    if (start < 0 || start + len > end) return false
    return (0..<len).all { i -> this[start + i] == s[i] }
  }
}

/**
 * [com.intellij.util.text.getCharsPlatformSpecificJvm]
 */
@Suppress("unused")
internal fun getCharsPlatformSpecific(sequence: CharSequence, srcOffset: Int, dst: CharArray, dstOffset: Int, len: Int): Boolean = linkToActual()

/**
 * [com.intellij.util.text.fromSequenceWithoutCopyingPlatformSpecificJvm]
 */
@Suppress("unused")
internal fun fromSequenceWithoutCopyingPlatformSpecific(seq: CharSequence?): CharArray? = linkToActual()

private const val GET_CHARS_THRESHOLD = 10
