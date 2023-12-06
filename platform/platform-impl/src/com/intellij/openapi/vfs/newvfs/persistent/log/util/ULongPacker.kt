// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.util

/**
 * ULongPacker's methods preserve binary representation of the original values
 */
object ULongPacker {
  fun ULong.setInt(value: Int, offset: Int, bits: Int): ULong {
    val uValue = value.toUInt() // preventing the sign bit extension
    assert(offset + bits <= ULong.SIZE_BITS)
    assert(uValue <= bitsMask(bits))
    return this or (uValue.toULong() shl offset)
  }

  fun ULong.getInt(offset: Int, bits: Int): Int =
    ((this shr offset) and bitsMask(bits))
      .toUInt().toInt() // intermediate conversion matters

  fun ULong.setLong(value: Long, offset: Int, bits: Int): ULong {
    val uValue = value.toULong() // preventing the sign bit extension
    assert(offset + bits <= ULong.SIZE_BITS)
    assert(uValue <= bitsMask(bits))
    return this or (uValue shl offset)
  }

  fun ULong.getLong(offset: Int, bits: Int): Long =
    ((this shr offset) and bitsMask(bits)).toLong()

  // first `bitsCount` least significant bits are 1
  private fun bitsMask(bitsCount: Int): ULong {
    assert(0 <= bitsCount && bitsCount <= ULong.SIZE_BITS)
    if (bitsCount == ULong.SIZE_BITS) return ULong.MAX_VALUE
    return (1.toULong() shl bitsCount).dec()
  }
}