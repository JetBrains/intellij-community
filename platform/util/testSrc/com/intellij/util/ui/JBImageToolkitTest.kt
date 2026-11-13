// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.scale.JBUIScale
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Image
import java.awt.MediaTracker
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import kotlin.math.roundToInt

@TestApplication
internal class JBImageToolkitTest {
  @Test
  fun `SVG keeps its raster pixels and reports its scale`() = forEachScale { scale ->
    val svg = """
      <svg xmlns="http://www.w3.org/2000/svg" width="17" height="13">
        <rect width="17" height="13" fill="red"/>
      </svg>
    """.trimIndent()
    val icon = ImageIcon(JBImageToolkit.createImage(svg.toByteArray()))

    assertThat(icon.imageLoadStatus).isEqualTo(MediaTracker.COMPLETE)
    assertThat(icon.iconWidth).isEqualTo((17 * scale).roundToInt())
    assertThat(icon.iconHeight).isEqualTo((13 * scale).roundToInt())
    assertThat(icon.image.getProperty(JBImageToolkit.IMAGE_SCALE_PROPERTY, null)).isEqualTo(scale.toDouble())
  }

  @Test
  fun `PNG keeps its native dimensions`() = forEachScale {
    val bytes = ByteArrayOutputStream().apply {
      ImageIO.write(BufferedImage(17, 13, BufferedImage.TYPE_INT_ARGB), "png", this)
    }.toByteArray()
    val icon = ImageIcon(JBImageToolkit.createImage(bytes))

    assertThat(icon.imageLoadStatus).isEqualTo(MediaTracker.COMPLETE)
    assertThat(icon.iconWidth).isEqualTo(17)
    assertThat(icon.iconHeight).isEqualTo(13)
    assertThat(icon.image.getProperty(JBImageToolkit.IMAGE_SCALE_PROPERTY, null)).isSameAs(Image.UndefinedProperty)
  }

  private fun forEachScale(test: (Float) -> Unit) {
    val originalScale = JBUIScale.sysScale()
    val jreHiDpi = JreHiDpiUtil.test_jreHiDPI()
    val originalHiDpi = jreHiDpi.getAndSet(true)
    try {
      for (scale in listOf(1f, 1.5f, 2f)) {
        JBUIScale.setSystemScaleFactor(scale)
        test(scale)
      }
    }
    finally {
      JBUIScale.setSystemScaleFactor(originalScale)
      jreHiDpi.set(originalHiDpi)
    }
  }
}
