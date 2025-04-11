// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import org.jetbrains.annotations.Contract
import kotlin.experimental.inv
import kotlin.jvm.JvmStatic

object BitUtil {
  @Contract(pure = true)
  @JvmStatic
  fun isSet(value: Byte, mask: Byte): Boolean {
    assertOneBitMask(mask)
    return (value.toInt() and mask.toInt()) == mask.toInt()
  }

  @Contract(pure = true)
  @JvmStatic
  fun isSet(value: Int, mask: Int): Boolean {
    assertOneBitMask(mask)
    return (value and mask) == mask
  }

  @Contract(pure = true)
  @JvmStatic
  fun isSet(flags: Long, mask: Long): Boolean {
    assertOneBitMask(mask)
    return (flags and mask) == mask
  }

  /**
   * @return `value` with the bit corresponding to the `mask` set (if setBit is true) or cleared (if setBit is false)
   */
  @Contract(pure = true)
  @JvmStatic
  fun set(value: Byte, mask: Byte, setBit: Boolean): Byte {
    assertOneBitMask(mask)
    val result = if (setBit) value.toInt() or mask.toInt() else value.toInt() and mask.inv().toInt()
    return result.toByte()
  }

  /**
   * @return `value` with the bit corresponding to the `mask` set (if setBit is true) or cleared (if setBit is false)
   */
  @Contract(pure = true)
  @JvmStatic
  fun set(value: Int, mask: Int, setBit: Boolean): Int {
    assertOneBitMask(mask)
    return if (setBit) value or mask else value and mask.inv()
  }

  /**
   * @return `value` with the bit corresponding to the `mask` set (if setBit is true) or cleared (if setBit is false)
   */
  @Contract(pure = true)
  @JvmStatic
  fun set(value: Long, mask: Long, setBit: Boolean): Long {
    assertOneBitMask(mask)
    return if (setBit) value or mask else value and mask.inv()
  }

  @Contract(pure = true)
  @JvmStatic
  fun clear(value: Byte, mask: Byte): Byte {
    return set(value, mask, false)
  }

  @Contract(pure = true)
  @JvmStatic
  fun clear(value: Int, mask: Int): Int {
    return set(value, mask, false)
  }

  @Contract(pure = true)
  @JvmStatic
  fun clear(value: Long, mask: Long): Long {
    return set(value, mask, false)
  }

  private fun assertOneBitMask(mask: Byte) {
    assertOneBitMask(mask.toLong() and 0xFFL)
  }

  @JvmStatic
  fun assertOneBitMask(mask: Int) {
    require((mask and mask - 1) == 0) { invalidMaskError(mask.toLong()) }
  }

  private fun assertOneBitMask(mask: Long) {
    require((mask and mask - 1) == 0L) { invalidMaskError(mask) }
  }

  private fun invalidMaskError(mask: Long): String = "Mask must have only one bit set, but got: $mask"
}