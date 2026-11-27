// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.indentGuide

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.paint.LinePainter2D
import org.junit.jupiter.api.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.test.assertTrue

@TestApplication
internal class IndentGuideLineTest {
  @Test
  fun `a guide line paints the pixels of LinePainter2D`() {
    val renderer = object : IndentGuideRenderer() {
      fun draw(g: Graphics2D, x: Int, y1: Int, y2: Int) {
        drawLine(g, x, y1, x, y2)
      }
    }
    val mismatches = ArrayList<String>()
    for (scale in doubleArrayOf(1.0, 1.25, 1.5, 2.0)) {
      for (antialiasing in ANTIALIASING) {
        for (strokeWidth in floatArrayOf(1f, 0.5f, 2f)) {
          for (x in 0..4) {
            for ((y1, y2) in listOf(3 to 3, 3 to 17, 17 to 3)) {
              val expected = paint(scale, antialiasing, strokeWidth) { g ->
                LinePainter2D.paint(g, x.toDouble(), y1.toDouble(), x.toDouble(), y2.toDouble())
              }
              val actual = paint(scale, antialiasing, strokeWidth) { g ->
                renderer.draw(g, x, y1, y2)
              }
              if (!sameImages(expected, actual)) {
                mismatches.add("scale=$scale antialiasing=$antialiasing stroke=$strokeWidth x=$x y=$y1..$y2")
              }
            }
          }
        }
      }
    }
    assertTrue(mismatches.isEmpty(), mismatches.joinToString("\n"))
  }

  private fun paint(scale: Double, antialiasing: Any, strokeWidth: Float, draw: (Graphics2D) -> Unit): BufferedImage {
    val image = BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      g.scale(scale, scale)
      g.translate(0, 7)
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing)
      g.stroke = BasicStroke(strokeWidth)
      g.color = Color.RED
      draw(g)
    }
    finally {
      g.dispose()
    }
    return image
  }

  private fun sameImages(expected: BufferedImage, actual: BufferedImage): Boolean {
    for (x in 0 until expected.width) {
      for (y in 0 until expected.height) {
        if (expected.getRGB(x, y) != actual.getRGB(x, y)) {
          return false
        }
      }
    }
    return true
  }

  private companion object {
    val ANTIALIASING: List<Any> = listOf(
      RenderingHints.VALUE_ANTIALIAS_OFF,
      RenderingHints.VALUE_ANTIALIAS_DEFAULT,
      RenderingHints.VALUE_ANTIALIAS_ON,
    )
  }
}
