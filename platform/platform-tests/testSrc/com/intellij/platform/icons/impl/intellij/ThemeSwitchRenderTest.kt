// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.openapi.util.IconLoader
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingLayerPaintingContext
import com.intellij.platform.icons.impl.rendering.DefaultIconRenderer
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.icons.pathTransform
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.UIManager

/** A `SwingIcon` that is kept across a live theme switch must paint the same pixels as a new `SwingIcon` built after the switch. */
@TestApplication
class ThemeSwitchRenderTest {
  companion object {
    private const val PATH = "expui/general/add.svg"
    private const val DARK_THEME_KEY = "ui.theme.is.dark"

    @JvmStatic
    @BeforeAll
    fun activateIconLoader() {
      IconLoader.activate()
    }

    @JvmStatic
    @AfterAll
    fun deactivateIconLoader() {
      IconLoader.deactivate()
    }
  }

  private val classLoader = ThemeSwitchRenderTest::class.java.classLoader

  @BeforeEach
  fun activateIconManager() {
    IntelliJIconManager.activate()
  }

  @Test
  fun `reused image icon paints the new theme`() {
    val descriptor = imageIconDescriptor(PATH, classLoader)
    val reused = descriptor.createSwingIcon()
    reused.paintToImage()

    withDarkTheme {
      val expected = descriptor.createSwingIcon().paintToImage()
      val actual = reused.paintToImage()
      assertEquals(expected.summary(), actual.summary(), "the reused icon paints other pixels than a new icon after the switch")
    }
  }

  @Test
  fun `renderer builds its layers once per theme switch`() {
    val flow = IconRendererManager.createUpdateFlow(null) {}
    val renderer = IconRendererManager.getInstance()
      .createRenderer(imageIconDescriptor(PATH, classLoader), IconRendererManager.createRenderingContext(flow)) as DefaultIconRenderer
    val icon = renderer.toSwingIconForTest()
    icon.paintToImage()

    withDarkTheme {
      icon.paintToImage()
      val afterFirstPaint = renderer.layerRenderers()
      icon.paintToImage()
      assertSame(afterFirstPaint, renderer.layerRenderers(), "the renderer built its layers again without a theme change")
    }
  }

  private fun <T> withDarkTheme(action: () -> T): T {
    val defaults = UIManager.getLookAndFeelDefaults()
    val previousKey = defaults.get(DARK_THEME_KEY)
    val previousDarkIcons = pathTransform.get().isDark
    defaults[DARK_THEME_KEY] = true
    IconLoader.setUseDarkIcons(true)
    try {
      return action()
    }
    finally {
      defaults[DARK_THEME_KEY] = previousKey
      IconLoader.setUseDarkIcons(previousDarkIcons)
    }
  }

  private fun Icon.paintToImage(): BufferedImage {
    val image = BufferedImage(iconWidth, iconHeight, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      paintIcon(null, g, 0, 0)
    }
    finally {
      g.dispose()
    }
    return image
  }

  /** The distinct opaque colors with their pixel counts. Readable in a failure message, and equal only for equal pixels in practice. */
  private fun BufferedImage.summary(): Map<String, Int> {
    val counts = sortedMapOf<String, Int>()
    for (y in 0 until height) {
      for (x in 0 until width) {
        val argb = getRGB(x, y)
        if (argb ushr 24 < 128) continue
        val key = "#%06X".format(argb and 0xFFFFFF)
        counts[key] = (counts[key] ?: 0) + 1
      }
    }
    return counts
  }

  private fun DefaultIconRenderer.layerRenderers(): Any? =
    DefaultIconRenderer::class.java.getDeclaredField("layerRenderers").apply { isAccessible = true }.get(this)

  private fun DefaultIconRenderer.toSwingIconForTest(): Icon = object : Icon {
    override fun getIconWidth() = 16
    override fun getIconHeight() = 16
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
      render(SwingLayerPaintingContext(c, g, x, y))
    }
  }
}
