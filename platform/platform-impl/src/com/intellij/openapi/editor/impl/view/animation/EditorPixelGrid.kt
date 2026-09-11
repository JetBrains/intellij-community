// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.ScaleContext
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D

/**
 * The device pixel grid the editor currently paints on. Two grids differ whenever a cached image would land on
 * different pixels than the content it replaces, which is when the cache has to be dropped.
 */
internal data class EditorPixelGrid(private val scaleContext: ScaleContext, private val alignment: Point2D) {
  /**
   * Grows [rectangle] so that every edge falls on a whole device pixel.
   */
  fun align(rectangle: Rectangle2D): Rectangle2D {
    val dx = alignment.x
    val dy = alignment.y
    val x0 = alignToInt(dx + rectangle.x, PaintUtil.RoundingMode.FLOOR)
    val y0 = alignToInt(dy + rectangle.y, PaintUtil.RoundingMode.FLOOR)
    val x1 = alignToInt(dx + rectangle.x + rectangle.width, PaintUtil.RoundingMode.CEIL)
    val y1 = alignToInt(dy + rectangle.y + rectangle.height, PaintUtil.RoundingMode.CEIL)
    // Now that we have everything aligned, shift back to the original misaligned space,
    // because that's the space that will be actually used for painting.
    return Rectangle2D.Double(x0 - dx, y0 - dy, x1 - x0, y1 - y0)
  }

  private fun alignToInt(value: Double, roundingMode: PaintUtil.RoundingMode): Double {
    return PaintUtil.alignToInt(value, scaleContext, roundingMode, null)
  }

  companion object {
    fun forComponent(editor: EditorImpl): EditorPixelGrid {
      val alignment = editor.contentComponent.currentAlignment
      return EditorPixelGrid(
        ScaleContext.create(editor.contentComponent),
        Point2D.Double(alignment.x, alignment.y),
      )
    }

    fun forGraphics(graphics: Graphics2D): EditorPixelGrid {
      val alignment = PaintUtil.getUserSpacePixelOffset(graphics) ?: Point2D.Double()
      return EditorPixelGrid(
        ScaleContext.create(graphics),
        Point2D.Double(alignment.x, alignment.y),
      )
    }
  }
}

internal fun Rectangle2D.intersectWithVisibleArea(visibleArea: Rectangle): Rectangle2D? {
  if (!intersects(visibleArea)) {
    return null
  }
  return createIntersection(visibleArea)
}
