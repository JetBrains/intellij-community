// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.ScaleContext
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D

internal data class EditorPixelGrid(val scaleContext: ScaleContext, val alignment: Point2D) {
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
  if (!intersects(visibleArea)) return null
  return createIntersection(visibleArea)
}

internal fun Rectangle2D.growToPixelGrid(pixelGrid: EditorPixelGrid): Rectangle2D {
  val scaleContext = pixelGrid.scaleContext
  val alignment = pixelGrid.alignment
  val dx = alignment.x
  val dy = alignment.y
  val x0 = PaintUtil.alignToInt(dx + x, scaleContext, PaintUtil.RoundingMode.FLOOR, null)
  val y0 = PaintUtil.alignToInt(dy + y, scaleContext, PaintUtil.RoundingMode.FLOOR, null)
  val x1 = PaintUtil.alignToInt(dx + x + width, scaleContext, PaintUtil.RoundingMode.CEIL, null)
  val y1 = PaintUtil.alignToInt(dy + y + height, scaleContext, PaintUtil.RoundingMode.CEIL, null)
  // Now that we have everything aligned, shift back to the original misaligned space,
  // because that's the space that will be actually used for painting.
  return Rectangle2D.Double(x0 - dx, y0 - dy, x1 - x0, y1 - y0)
}