/*
 * Copyright (C) 2015 The Android Open Source Project
 * Modified 2026 by JetBrains s.r.o.
 * Copyright (C) 2026 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.resources.vectorDrawable.svgConverter

import com.intellij.xml.util.ColorMap
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Based on [com.android.ide.common.vectordrawable.SvgColor]
 * Java source: https://cs.android.com/android-studio/platform/tools/base/+/bded3d40b59130a2c2a6f663025c88ecb6221e63:sdk-common/src/main/java/com/android/ide/common/vectordrawable/SvgColor.java
 * Key differences from Android original:
 * - Removed the hardcoded map of SVG color names and delegated resolution to IntelliJ's built-in [ColorMap]
 * - Replaced manual `StringBuilder` logic with Kotlin's [joinToString]
 * - Simplified manual size checks and IllegalArgumentException throws using Kotlin's [require]
 * - Flattened mathematical coercions using Kotlin's [coerceIn] and [roundToInt]
 */
internal fun colorSvg2Vd(svgColorValue: String): String? {
  val color = svgColorValue.trim()

  if (color.startsWith("#")) {
    if (color.length == 5) return "#${color.substring(4)}${color.substring(1, 4)}"
    if (color.length == 9) return "#${color.substring(7)}${color.substring(1, 7)}"
    return color
  }

  if (color == "none") return "#00000000"

  if (color.startsWith("rgb(") && color.endsWith(")")) {
    val numbers = color.substring(4, color.length - 1).split(",")
    require(numbers.size == 3) { svgColorValue }

    return numbers.joinToString(separator = "", prefix = "#") {
      String.format("%02X", getColorComponent(it.trim(), svgColorValue))
    }
  }

  if (color.startsWith("rgba(") && color.endsWith(")")) {
    val numbers = color.substring(5, color.length - 1).split(",").map { it.trim() }
    require(numbers.size == 4) { svgColorValue }

    val argbOrder = listOf(numbers[3], numbers[0], numbers[1], numbers[2])
    return argbOrder.joinToString(separator = "", prefix = "#") {
      String.format("%02X", getColorComponent(it, svgColorValue))
    }
  }

  return ColorMap.getHexCodeForColorName(color.lowercase(Locale.ENGLISH))?.lowercase()
}

private fun getColorComponent(colorComponent: String, svgColorValue: String): Int {
  try {
    if (!colorComponent.endsWith("%")) return clampColor(colorComponent.toInt())

    val value = colorComponent.substring(0, colorComponent.length - 1).toFloat()
    return clampColor((value * 255f / 100f).roundToInt())
  }
  catch (_: NumberFormatException) {
    throw IllegalArgumentException(svgColorValue)
  }
}

private fun clampColor(value: Int): Int {
  return value.coerceIn(0, 255)
}
