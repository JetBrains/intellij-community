package com.intellij.openapi.wm.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.Rectangle
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import javax.swing.JPanel
import javax.swing.SwingUtilities

internal class WindowMaskTest {
  @Test
  fun `null and empty bounds remove the mask`() {
    assertThat(WindowMask.rectangles(null)).isNull()
    assertThat(WindowMask.rectangles(Rectangle())).isNull()
  }

  @Test
  fun `adjacent identical rows form one rectangle`() {
    assertThat(WindowMask.rectangles(Rectangle(12, 8, 15, 7))).containsExactly(Rectangle(12, 8, 15, 7))
  }

  @Test
  fun `negative coordinates are clipped at the window origin`() {
    assertThat(WindowMask.rectangles(Rectangle(-4, -3, 7, 5))).containsExactly(Rectangle(0, 0, 3, 2))
    assertThat(WindowMask.rectangles(Rectangle(-8, 2, 3, 4))).isEmpty()
  }

  @Test
  fun `holes produce ordered nonoverlapping bands`() {
    val shape = Area(Rectangle(2, 3, 8, 7))
    shape.subtract(Area(Rectangle(4, 5, 3, 2)))
    assertThat(WindowMask.rectangles(shape)).containsExactly(
      Rectangle(2, 3, 8, 2), Rectangle(2, 5, 2, 2), Rectangle(7, 5, 3, 2), Rectangle(2, 7, 8, 3),
    )
  }

  @Test
  fun `curved masks match the raster without overlapping rectangles`() {
    val shape = Ellipse2D.Double(3.5, 4.5, 13.5, 9.5)
    val expected = BufferedImage(20, 20, BufferedImage.TYPE_BYTE_BINARY)
    val graphics = expected.createGraphics()
    try {
      graphics.color = Color.WHITE
      graphics.fill(shape)
    }
    finally {
      graphics.dispose()
    }
    val rectangles = checkNotNull(WindowMask.rectangles(shape))
    for (row in 0 until expected.height) {
      for (column in 0 until expected.width) {
        assertThat(rectangles.count { it.contains(column, row) }).describedAs("pixel %s,%s", column, row)
          .isEqualTo(expected.raster.getSample(column, row, 0))
      }
    }
  }

  @Test
  fun `macOS masking clears excluded pixels and preserves the caller clip`() {
    SwingUtilities.invokeAndWait {
      val content = JPanel().apply { background = Color.RED }
      val panel = MacWindowMask(content)
      panel.setSize(8, 8)
      panel.doLayout()
      panel.mask = Rectangle(0, 0, 4, 8)
      val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
      val graphics = image.createGraphics()
      try {
        graphics.color = Color.BLUE
        graphics.fillRect(0, 0, 8, 8)
        graphics.clip = Rectangle(0, 0, 8, 4)
        panel.paint(graphics)
        assertThat(image.getRGB(2, 2)).isEqualTo(Color.RED.rgb)
        assertThat(image.getRGB(6, 2)).isZero()
        assertThat(image.getRGB(2, 6)).isEqualTo(Color.BLUE.rgb)
        assertThat(graphics.clip.bounds).isEqualTo(Rectangle(0, 0, 8, 4))

        panel.mask = null
        panel.paint(graphics)
        assertThat(image.getRGB(6, 2)).isEqualTo(Color.RED.rgb)
      }
      finally {
        graphics.dispose()
      }
    }
  }
}
