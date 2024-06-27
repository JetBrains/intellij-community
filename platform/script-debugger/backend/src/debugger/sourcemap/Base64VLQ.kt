// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger.sourcemap

import org.jetbrains.annotations.NonNls

internal object Base64VLQ {
  // A Base64 VLQ digit can represent 5 bits, so it is base-32.
  private const val VLQ_BASE_SHIFT = 5
  private const val VLQ_BASE = 1 shl VLQ_BASE_SHIFT

  // A mask of bits for a VLQ digit (11111), 31 decimal.
  private const val VLQ_BASE_MASK = VLQ_BASE - 1

  // The continuation bit is the 6th bit.
  private const val VLQ_CONTINUATION_BIT = VLQ_BASE

  /**
   * Decodes the next VLQValue from the provided CharIterator.
   */
  fun decode(`in`: CharIterator): Int {
    var result = 0
    var shift = 0
    var digit: Int
    do {
      digit = Base64.BASE64_DECODE_MAP[`in`.next().code]
      assert(digit != -1) { "invalid char" }

      result += (digit and VLQ_BASE_MASK) shl shift
      shift += VLQ_BASE_SHIFT
    }
    while ((digit and VLQ_CONTINUATION_BIT) != 0)

    val negate = (result and 1) == 1
    result = result shr 1
    return if (negate) -result else result
  }

  internal interface CharIterator {
    fun hasNext(): Boolean
    fun next(): Char
  }

  private object Base64 {
    /**
     * A map used to convert integer values in the range 0-63 to their base64
     * values.
     */
    private const val BASE64_MAP: @NonNls String = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                                                   "abcdefghijklmnopqrstuvwxyz" +
                                                   "0123456789+/"

    /**
     * A map used to convert base64 character into integer values.
     */
    val BASE64_DECODE_MAP: IntArray = IntArray(256)

    init {
      for (i in 0 until BASE64_MAP.length) {
        BASE64_DECODE_MAP[BASE64_MAP[i].code] = i
      }
    }
  }
}