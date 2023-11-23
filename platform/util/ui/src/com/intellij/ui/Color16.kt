// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.util.MathUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Color

/**
 * Note that there are no checks for overflows in the operators' implementation. It is intended.
 */
@Internal
data class Color16(val red: Int, val green: Int, val blue: Int, val alpha: Int) {
  @Suppress("UseJBColor")
  fun toColor8(): Color {
    return Color(to8bit(red), to8bit(green), to8bit(blue), to8bit(alpha))
  }

  private fun to8bit(value: Int): Int {
    val result = value / 256 + if (value % 256 >= 128) 1 else 0
    return MathUtil.clamp(result, 0, 255)
  }

  operator fun minus(other: Color16): Color16 {
    return Color16(red - other.red, green - other.green, blue - other.blue, alpha - other.alpha)
  }

  operator fun plus(other: Color16): Color16 {
    return Color16(red + other.red, green + other.green, blue + other.blue, alpha + other.alpha)
  }

  operator fun times(multiplier: Double): Color16 {
    return Color16((red * multiplier).toInt(), (green * multiplier).toInt(), (blue * multiplier).toInt(), (alpha * multiplier).toInt())
  }

  companion object {
    val TRANSPARENT: Color16 = Color16(0, 0, 0, 0)

    fun Color.toColor16(): Color16 {
      return Color16(red * 256, green * 256, blue * 256, alpha * 256)
    }
  }
}
