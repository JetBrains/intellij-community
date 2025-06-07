// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.themePicker

import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Point
import java.awt.Rectangle

internal class PixelColorMap {
  // we may introduce a better data structure here, but ALAS, this is not a problem
  private val colorRectangles = mutableListOf<Pair<Rectangle, Color>>()

  fun mark(rectangle: Rectangle, color: Color, erase: Boolean) {
    val gapPx = JBUI.scale(5) // a nicer clickable area
    val gappedRectangle = Rectangle(rectangle)
    gappedRectangle.x -= gapPx
    gappedRectangle.y -= gapPx
    gappedRectangle.width += gapPx * 2
    gappedRectangle.height += gapPx * 2

    if (erase) {
      colorRectangles.removeAll { (rect, _) -> gappedRectangle.contains(rect) }
    }

    colorRectangles.add(Pair(gappedRectangle, color))
  }

  fun getColor(point: Point): List<ThemeColorInfo>? {
    if (colorRectangles.isEmpty()) return null

    val colors = colorRectangles
                   .filter { (rect, _) -> rect.contains(point) }
                   .map { (_, color) -> color }
                   .distinct()
                   .takeIf { it.isNotEmpty() } ?: return null
    return colors.map { ThemeColorInfo.ColorInfo(it) }
  }
}
