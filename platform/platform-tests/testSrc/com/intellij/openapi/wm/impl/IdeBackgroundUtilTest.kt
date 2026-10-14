// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBSwingUtilities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.JPanel
import kotlin.math.ceil

class IdeBackgroundUtilTest {
  @ParameterizedTest
  @CsvSource("2.0, false", "2.0, true", "1.5, true")
  fun `fillRect paints the same pixels as the translucent background path`(
    scale: Double,
    antialiasing: Boolean,
    @TestDisposable disposable: Disposable,
  ) {
    val image = BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB)
    image.createGraphics().apply {
      paint = GradientPaint(0f, 0f, Color(30, 60, 120, 40), W.toFloat(), H.toFloat(), Color(120, 40, 80, 255))
      fillRect(0, 0, W, H)
      dispose()
    }
    val (translucent, precomposed) = runInEdtAndGet {
      val root = JPanel(null).apply { setBounds(0, 0, W, H) }
      JPanel(null).add(root)  // the painter skips a root without a parent
      IdeBackgroundUtil.createTemporaryBackgroundTransform(root, image, IdeBackgroundUtil.Fill.SCALE, IdeBackgroundUtil.Anchor.CENTER,
                                                           0.3f, JBInsets.emptyInsets(), disposable)

      // fill(Shape) always uses the translucent path; fillRect uses the precomposed opaque path when the fill qualifies.
      // The rectangle starts at a fractional device coordinate for a fractional scale.
      render(root, scale, antialiasing) { fill(Rectangle(0, 3, W, 31)) } to
        render(root, scale, antialiasing) { fillRect(0, 3, W, 31) }
    }

    assertThat(translucent.any { it != BASE.rgb && it != UNDER.rgb }).describedAs("the background image is painted").isTrue()
    assertThat(precomposed).isEqualTo(translucent)
  }

  private fun render(root: JPanel, scale: Double, antialiasing: Boolean, fill: Graphics2D.() -> Unit): IntArray {
    val out = BufferedImage(ceil(W * scale).toInt(), ceil(H * scale).toInt(), BufferedImage.TYPE_INT_ARGB_PRE)
    val g = out.createGraphics()
    g.scale(scale, scale)
    g.color = UNDER
    g.fillRect(0, 0, W, H)
    if (antialiasing) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    }
    val wrapped = JBSwingUtilities.runGlobalCGTransform(root, g)
    wrapped.color = BASE
    // a base color gets a precomposed image only after its fills covered the image area, then the cache serves it
    repeat(8) { wrapped.fill() }
    g.dispose()
    return out.getRGB(0, 0, out.width, out.height, null, 0, out.width)
  }

  private companion object {
    const val W = 200
    const val H = 100
    val BASE = Color(43, 43, 43)
    val UNDER = Color(200, 0, 0)
  }
}
