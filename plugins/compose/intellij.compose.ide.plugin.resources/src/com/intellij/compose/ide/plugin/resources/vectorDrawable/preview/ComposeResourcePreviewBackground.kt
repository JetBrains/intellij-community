// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.vectorDrawable.preview

import com.intellij.ui.Gray
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Graphics2D

internal enum class ComposeResourcePreviewBackground {
  NONE,
  WHITE,
  BLACK,
  CHECKERED,
}

private const val CHECKERED_CELL_SIZE = 12

private val CHECKERED_LIGHT_COLOR: Color = Color.WHITE
private val CHECKERED_DARK_COLOR: Color = Gray._236

internal object ComposeResourcePreviewBackgroundPainter {

  fun paint(g: Graphics2D, background: ComposeResourcePreviewBackground, width: Int, height: Int) {
    if (width <= 0 || height <= 0) return

    g.color = when (background) {
      ComposeResourcePreviewBackground.NONE -> return
      ComposeResourcePreviewBackground.WHITE -> Color.WHITE
      ComposeResourcePreviewBackground.BLACK -> Color.BLACK
      ComposeResourcePreviewBackground.CHECKERED -> {
        paintCheckered(g, width, height)
        return
      }
    }
    g.fillRect(0, 0, width, height)
  }

  private fun paintCheckered(g: Graphics2D, width: Int, height: Int) {
    val cellSize = JBUI.scale(CHECKERED_CELL_SIZE)

    g.color = CHECKERED_LIGHT_COLOR
    g.fillRect(0, 0, width, height)
    g.color = CHECKERED_DARK_COLOR

    var isOddRow = false
    for (y in 0 until height step cellSize) {
      val xStart = if (isOddRow) 0 else cellSize
      for (x in xStart until width step 2 * cellSize) {
        g.fillRect(x, y, cellSize, cellSize)
      }
      isOddRow = !isOddRow
    }
  }
}
