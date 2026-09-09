package com.intellij.openapi.wm.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.plaf.ColorUIResource

@EnabledOnOs(OS.MAC)
internal class MacWindowMaskTest {
  @Test
  fun `repeated masks preserve the original background until reset`() {
    assumeFalse(GraphicsEnvironment.isHeadless())
    SwingUtilities.invokeAndWait {
      val window = JWindow()
      try {
        window.background = ColorUIResource(Color.BLUE)
        val panel = MacWindowMask(window.contentPane)
        window.contentPane = panel
        panel.apply(window, Rectangle(0, 0, 10, 10))
        panel.apply(window, Rectangle(1, 1, 8, 8))
        assertThat(window.background.alpha).isZero()
        assertThat(window.rootPane.getClientProperty("apple.awt.draggableWindowBackground")).isEqualTo(false)
        panel.apply(window, null)
        assertThat(window.background).isEqualTo(Color.BLUE).isNotInstanceOf(ColorUIResource::class.java)
        assertThat(panel.mask).isNull()

        window.background = Color.GREEN
        panel.apply(window, Rectangle(2, 2, 4, 4))
        panel.apply(window, null)
        assertThat(window.background).isEqualTo(Color.GREEN)
      }
      finally {
        window.dispose()
      }
    }
  }

  @Test
  fun `mask reset preserves an unmodified background and explicit dragging property`() {
    assumeFalse(GraphicsEnvironment.isHeadless())
    SwingUtilities.invokeAndWait {
      val window = JWindow()
      try {
        window.background = Color.BLUE
        window.rootPane.putClientProperty("apple.awt.draggableWindowBackground", true)
        val panel = MacWindowMask(window.contentPane)
        window.contentPane = panel
        panel.apply(window, null)
        assertThat(window.background).isEqualTo(Color.BLUE)
        assertThat(window.rootPane.getClientProperty("apple.awt.draggableWindowBackground")).isEqualTo(true)
      }
      finally {
        window.dispose()
      }
    }
  }
}
