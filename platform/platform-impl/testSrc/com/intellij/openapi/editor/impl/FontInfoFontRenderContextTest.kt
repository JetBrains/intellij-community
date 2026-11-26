// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.FontMetrics
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.image.ColorModel
import javax.swing.JComponent

internal class FontInfoFontRenderContextTest {
  @Test
  fun `a Swing component gives the context of its font metrics`() {
    for (transform in TRANSFORMS) {
      for (antialiasing in ANTIALIASING_HINTS) {
        for (fractionalMetrics in FRACTIONAL_METRICS_HINTS) {
          val component = TestComponent(transform?.let(::TestGraphicsConfiguration))
          component.putClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING, antialiasing)
          component.putClientProperty(RenderingHints.KEY_FRACTIONALMETRICS, fractionalMetrics)
          val fontMetricsContext = component.getFontMetrics(Font(Font.DIALOG, Font.PLAIN, 12)).fontRenderContext
          assertThat(FontInfo.getFontRenderContext(component))
            .describedAs("transform $transform, antialiasing $antialiasing, fractional metrics $fractionalMetrics")
            .isEqualTo(fontMetricsContext)
        }
      }
    }
  }

  @Test
  fun `a Swing component with an identity transform and without hints gives the default context`() {
    val component = TestComponent(TestGraphicsConfiguration(AffineTransform()))
    assertThat(FontInfo.getFontRenderContext(component)).isEqualTo(FontRenderContext(null, false, false))
  }

  @Test
  fun `a Swing component gives its context without its font metrics`() {
    val scale = AffineTransform.getScaleInstance(2.0, 2.0)
    val component = object : TestComponent(TestGraphicsConfiguration(scale)) {
      override fun getFontMetrics(font: Font): FontMetrics = throw AssertionError("font metrics of $font")
    }
    component.putClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
    val expected = FontRenderContext(scale, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB, RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT)
    assertThat(FontInfo.getFontRenderContext(component)).isEqualTo(expected)
  }

  @Test
  fun `without a component the context is the default context`() {
    assertThat(FontInfo.getFontRenderContext(null)).isEqualTo(FontRenderContext(null, false, false))
  }

  /**
   * A component with the given graphics configuration. Without a configuration, it is not on a screen.
   */
  private open class TestComponent(private val configuration: GraphicsConfiguration?) : JComponent() {
    override fun getGraphicsConfiguration(): GraphicsConfiguration? = configuration
  }

  /**
   * A graphics configuration that gives only its default transform.
   */
  private class TestGraphicsConfiguration(private val transform: AffineTransform) : GraphicsConfiguration() {
    override fun getDefaultTransform(): AffineTransform = AffineTransform(transform)

    override fun getDevice(): GraphicsDevice = throw UnsupportedOperationException()

    override fun getColorModel(): ColorModel = throw UnsupportedOperationException()

    override fun getColorModel(transparency: Int): ColorModel = throw UnsupportedOperationException()

    override fun getNormalizingTransform(): AffineTransform = throw UnsupportedOperationException()

    override fun getBounds(): Rectangle = throw UnsupportedOperationException()
  }

  private companion object {
    val TRANSFORMS: List<AffineTransform?> = listOf(
      null,
      AffineTransform(),
      AffineTransform.getScaleInstance(1.25, 1.25),
      AffineTransform.getScaleInstance(2.0, 2.0),
    )

    val ANTIALIASING_HINTS: List<Any?> = listOf(
      null,
      RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
      RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
      RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT,
      RenderingHints.VALUE_TEXT_ANTIALIAS_GASP,
      RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB,
      RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VBGR,
    )

    val FRACTIONAL_METRICS_HINTS: List<Any?> = listOf(
      null,
      RenderingHints.VALUE_FRACTIONALMETRICS_OFF,
      RenderingHints.VALUE_FRACTIONALMETRICS_ON,
      RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT,
    )
  }
}
