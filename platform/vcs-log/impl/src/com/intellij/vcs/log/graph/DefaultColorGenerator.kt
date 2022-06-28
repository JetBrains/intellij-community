// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph

import com.intellij.ui.JBColor
import com.intellij.vcs.log.paint.ColorGenerator
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import java.awt.Color
import java.util.function.IntFunction
import kotlin.math.abs

/**
 * @author erokhins
 */
class DefaultColorGenerator : ColorGenerator {
  override fun getColor(colorId: Int): JBColor {
    return ourColorMap.computeIfAbsent(colorId, IntFunction { calcColor(it) })
  }

  companion object {
    private val ourColorMap = Int2ObjectOpenHashMap<JBColor>().apply {
      put(GraphColorManagerImpl.DEFAULT_COLOR, JBColor.BLACK)
    }

    private fun calcColor(colorId: Int): JBColor {
      val r = colorId * 200 + 30
      val g = colorId * 130 + 50
      val b = colorId * 90 + 100
      return try {
        val color = Color(rangeFix(r), rangeFix(g), rangeFix(b))
        JBColor(color, color)
      }
      catch (a: IllegalArgumentException) {
        throw IllegalArgumentException("Color: $colorId ${r % 256} ${g % 256} ${b % 256}")
      }
    }

    private fun rangeFix(n: Int) = abs(n % 100) + 70
  }
}