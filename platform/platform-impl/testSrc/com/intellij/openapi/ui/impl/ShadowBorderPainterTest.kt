// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.impl

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

internal class ShadowBorderPainterTest {
  @Test
  fun `the shadow is larger by twice the size on each axis`() {
    val shadow = ShadowBorderPainter.blurAlpha(opaqueSquare(side = 10, margin = 0), 3)

    assertThat(shadow.width).isEqualTo(16)
    assertThat(shadow.height).isEqualTo(16)
    assertThat(shadow.type).isEqualTo(BufferedImage.TYPE_INT_ARGB)
  }

  @Test
  fun `an opaque interior gets the opacity in black`() {
    val shadow = ShadowBorderPainter.blurAlpha(opaqueSquare(side = 12, margin = 0), 2)

    // 0.2 of 255
    assertThat(shadow.getRGB(8, 8)).isEqualTo(51 shl 24)
    assertThat(shadow.getRGB(0, 0)).isZero()
  }

  @Test
  fun `the shadow is symmetric around the source centre with a half-pixel shift`() {
    val size = 4
    val shadow = ShadowBorderPainter.blurAlpha(opaqueSquare(side = 9, margin = 3), size)
    val width = shadow.width
    val height = shadow.height

    for (i in 0 until width) {
      assertThat(alpha(shadow, i, 0)).describedAs("top row at $i").isZero()
      assertThat(alpha(shadow, 0, i)).describedAs("left column at $i").isZero()
    }
    for (y in 1 until height) {
      for (x in 1 until width) {
        assertThat(alpha(shadow, x, y)).describedAs("($x, $y)").isEqualTo(alpha(shadow, width - x, height - y))
      }
    }
  }

  @Test
  fun `a size below one is refused`() {
    assertThatThrownBy { ShadowBorderPainter.blurAlpha(opaqueSquare(side = 4, margin = 0), 0) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("must be positive")
  }
}

private fun alpha(image: BufferedImage, x: Int, y: Int): Int = image.getRGB(x, y) ushr 24

private fun opaqueSquare(side: Int, margin: Int): BufferedImage {
  val image = BufferedImage(side + 2 * margin, side + 2 * margin, BufferedImage.TYPE_INT_ARGB)
  for (y in margin until margin + side) {
    for (x in margin until margin + side) {
      image.setRGB(x, y, 0xFFFFFFFF.toInt())
    }
  }
  return image
}
