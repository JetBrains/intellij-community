package com.intellij.openapi.wm.impl

import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Rectangle
import java.awt.Shape
import java.awt.image.BufferedImage

@ApiStatus.Internal
object WindowMask {
  fun rectangles(shape: Shape?): List<Rectangle>? {
    val bounds = shape?.bounds ?: return null
    if (bounds.isEmpty) return null
    val right = Math.addExact(bounds.x, bounds.width)
    val bottom = Math.addExact(bounds.y, bounds.height)
    val left = bounds.x.coerceAtLeast(0)
    val top = bounds.y.coerceAtLeast(0)
    if (right <= left || bottom <= top) return emptyList()

    val image = BufferedImage(right - left, bottom - top, BufferedImage.TYPE_BYTE_BINARY)
    val graphics = image.createGraphics()
    try {
      graphics.color = Color.WHITE
      graphics.translate(-left, -top)
      graphics.fill(shape)
    }
    finally {
      graphics.dispose()
    }

    val rectangles = ArrayList<Rectangle>()
    var previous = emptyList<Rectangle>()
    val samples = IntArray(image.width)
    for (row in 0 until image.height) {
      image.raster.getSamples(0, row, image.width, 1, 0, samples)
      val current = ArrayList<Rectangle>()
      var column = 0
      while (column < samples.size) {
        if (samples[column] == 0) {
          column++
          continue
        }
        val start = column
        while (column < samples.size && samples[column] != 0) column++
        current.add(Rectangle(left + start, top + row, column - start, 1))
      }
      if (current.size == previous.size && current.indices.all {
          current[it].x == previous[it].x && current[it].width == previous[it].width
        }) {
        previous.forEach { it.height++ }
      }
      else {
        rectangles.addAll(current)
        previous = current
      }
    }
    return rectangles
  }
}
