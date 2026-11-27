// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.ui.Graphics2DDelegate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Composite
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.ceil

internal class IslandsToolWindowTintTest {
  @TestFactory
  fun `precomposed tint paints the same pixels as the translucent tint`(): List<DynamicTest> =
    listOf(1.0 to 0.2f, 2.0 to 0.2f, 1.5 to 0.2f, 1.5 to 0.11f).map { (scale, alpha) ->
      dynamicTest("scale $scale, alpha $alpha") { checkTint(scale, alpha) }
    }

  private fun checkTint(scale: Double, alpha: Float) {
    val tintComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
    val translucent = render(scale) {
      composite = tintComposite
      assertThat(fillTint(this)).describedAs("the composite of the translucent fill").isEqualTo(tintComposite)
      assertThat(composite).describedAs("the composite after the fill").isEqualTo(tintComposite)
    }
    val precomposed = render(scale) {
      setRenderingHint(IdeBackgroundUtil.BASE_COLOR_HINT, BASE)
      composite = tintComposite
      assertThat(fillTint(this)).describedAs("the composite of the precomposed fill").isEqualTo(AlphaComposite.SrcOver)
      assertThat(composite).describedAs("the composite after the fill").isEqualTo(tintComposite)
    }

    assertThat(translucent.any { it != BASE.rgb && it != UNDER.rgb }).describedAs("the tint is painted").isTrue()
    for (i in translucent.indices) {
      assertThat(maxChannelDifference(translucent[i], precomposed[i])).describedAs("pixel $i").isLessThanOrEqualTo(1)
    }
  }

  private fun render(scale: Double, paintTint: Graphics2D.() -> Unit): IntArray {
    val out = BufferedImage(ceil(W * scale).toInt(), ceil(H * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.scale(scale, scale)
    g.color = UNDER
    g.fillRect(0, 0, W, H)
    // the base fill and the painter clip start at a fractional device coordinate for a fractional scale
    g.color = BASE
    g.fillRect(FILL.x, FILL.y, FILL.width, FILL.height)
    g.clip(FILL)
    g.paintTint()
    g.dispose()
    return out.getRGB(0, 0, out.width, out.height, null, 0, out.width)
  }

  /**
   * Calls [fillToolWindowTint] and returns the composite of its fill.
   */
  private fun fillTint(g: Graphics2D): Composite? {
    val recorder = FillCompositeRecorder(g)
    fillToolWindowTint(recorder, TINT, FILL)
    return recorder.fillComposite
  }

  private fun maxChannelDifference(rgb1: Int, rgb2: Int): Int =
    (0..24 step 8).maxOf { shift -> abs((rgb1 shr shift and 0xFF) - (rgb2 shr shift and 0xFF)) }

  private class FillCompositeRecorder(g: Graphics2D) : Graphics2DDelegate(g) {
    var fillComposite: Composite? = null

    override fun fillRect(x: Int, y: Int, width: Int, height: Int) {
      fillComposite = composite
      super.fillRect(x, y, width, height)
    }
  }

  private companion object {
    const val W = 200
    const val H = 100
    val FILL = Rectangle(10, 3, 150, 31)
    val BASE = Color(43, 45, 48)
    val TINT = Color(180, 90, 30)
    val UNDER = Color(200, 0, 0)
  }
}
