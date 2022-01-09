// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph

import com.intellij.ui.JBColor
import com.intellij.vcs.log.paint.ColorGenerator
import java.awt.Color
import kotlin.math.abs

/**
 * @author erokhins
 */
class DefaultColorGenerator : ColorGenerator {
  override fun getColor(branchNumber: Int): JBColor {
    var color = ourColorMap[branchNumber]
    if (color == null) {
      color = calcColor(branchNumber)
      ourColorMap[branchNumber] = color
    }
    return color
  }

  companion object {
    private val ourColorMap: MutableMap<Int, JBColor> = HashMap()

    init {
      ourColorMap[GraphColorManagerImpl.DEFAULT_COLOR] = JBColor.BLACK
    }

    private fun rangeFix(n: Int): Int {
      return abs(n % 100) + 70
    }

    private fun calcColor(indexColor: Int): JBColor {
      val r = indexColor * 200 + 30
      val g = indexColor * 130 + 50
      val b = indexColor * 90 + 100
      return try {
        val color = Color(rangeFix(r), rangeFix(g), rangeFix(b))
        JBColor(color, color)
      }
      catch (a: IllegalArgumentException) {
        throw IllegalArgumentException("indexColor: $indexColor ${r % 256} ${g % 256} ${b % 256}")
      }
    }
  }
}