// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.util.IconLoader
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.ImageDescriptor
import com.intellij.ui.icons.decodeCachedImageIconFromByteArray
import com.intellij.ui.icons.loadRasterizedIcon
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.ui.svg.SvgCacheManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.swing.Icon

/**
 * Checks the icons that pass the cache key and the flags of their New UI path.
 */
@TestApplication
class ExpUIRasterizedIconTest {
  @BeforeEach
  fun setUp() {
    assumeTrue(NewUiValue.isEnabled(), "the New UI is off")
    IconLoader.activate()
  }

  @AfterEach
  fun tearDown() {
    if (GraphicsEnvironment.isHeadless()) {
      IconLoader.deactivate()
    }
  }

  @Test
  fun `image flags are the New UI flags`() {
    val icon = loadIcon(expUIFlags = ImageDescriptor.HAS_DARK or ImageDescriptor.HAS_STROKE)
    assertThat(icon.imageFlags).isEqualTo(ImageDescriptor.HAS_DARK or ImageDescriptor.HAS_STROKE)
  }

  @Test
  fun `image flags are the original flags without the New UI data`() {
    val icon = loadIconWithoutExpUIData(expUIPath = EXP_UI_PATH)
    assertThat(icon.imageFlags).isEqualTo(ORIGINAL_FLAGS)
  }

  @Test
  fun `New UI flags select the dark variant`() {
    val light = loadFileIcon(EXP_UI_PATH).pixels()
    val dark = loadFileIcon(EXP_UI_DARK_PATH).pixels()
    assertThat(dark).isNotEqualTo(light)

    assertThat(loadIcon(expUIFlags = ImageDescriptor.HAS_DARK).getDarkIcon(true).pixels()).isEqualTo(dark)
    assertThat(loadIcon(expUIFlags = 0).getDarkIcon(true).pixels()).isEqualTo(light)
  }

  @Test
  fun `New UI flags select the stroke variant`() {
    val stroke = loadFileIcon(EXP_UI_STROKE_PATH).pixels()
    assertThat(stroke).isNotEqualTo(loadFileIcon(EXP_UI_PATH).pixels())

    val icon = loadIcon(expUIFlags = ImageDescriptor.HAS_STROKE).getDarkIcon(false)
    assertThat(toStrokeIcon(icon, Color.WHITE).pixels()).isEqualTo(stroke)
  }

  @Test
  @SystemProperty(propertyKey = "idea.ui.icons.svg.disk.cache", propertyValue = "true")
  fun `New UI cache key finds the image in the disk cache without the file`(@TempDir dir: Path) {
    val cache = timeoutRunBlocking { SvgCacheManager.createSvgCacheManager(dir.resolve("icon-cache.db"))!! }
    val previousCache = SvgCacheManager.svgCache
    SvgCacheManager.svgCache = cache
    try {
      // the existing file puts its image into the disk cache under the key
      val existing = loadIcon(expUICacheKey = EXP_UI_CACHE_KEY, expUIFlags = 0)
      assertThat(existing.iconWidth).isGreaterThan(1)

      val missing = loadIcon(expUIPath = MISSING_EXP_UI_PATH, expUICacheKey = EXP_UI_CACHE_KEY, expUIFlags = 0)
      assertThat(missing.pixels()).isEqualTo(existing.pixels())

      // the empty icon has the width 1
      assertThat(loadIconWithoutExpUIData(expUIPath = MISSING_EXP_UI_PATH).iconWidth).isEqualTo(1)
    }
    finally {
      SvgCacheManager.svgCache = previousCache
      cache.close()
    }
  }

  @Test
  fun `serialization keeps the New UI data`() {
    val icon = loadIcon(expUIFlags = ImageDescriptor.HAS_DARK)
    val decoded = decodeCachedImageIconFromByteArray(icon.encodeToByteArray()!!) as CachedImageIcon
    assertThat(decoded.imageFlags).isEqualTo(ImageDescriptor.HAS_DARK)
    assertThat(decoded.getDarkIcon(true).pixels()).isEqualTo(loadFileIcon(EXP_UI_DARK_PATH).pixels())
  }
}

private const val ORIGINAL_PATH = "expUIRasterizedIconTest/original.svg"
private const val ORIGINAL_CACHE_KEY = 11
private const val ORIGINAL_FLAGS = ImageDescriptor.HAS_2x
private const val EXP_UI_PATH = "expui/general/moreVertical.svg"
private const val EXP_UI_DARK_PATH = "expui/general/moreVertical_dark.svg"
private const val EXP_UI_STROKE_PATH = "expui/general/moreVertical_stroke.svg"
private const val MISSING_EXP_UI_PATH = "expui/expUIRasterizedIconTest/missing.svg"
private const val EXP_UI_CACHE_KEY = 42

private val classLoader: ClassLoader
  get() = ExpUIRasterizedIconTest::class.java.classLoader

private fun loadIcon(expUIPath: String = EXP_UI_PATH, expUICacheKey: Int = EXP_UI_CACHE_KEY, expUIFlags: Int): CachedImageIcon {
  return loadRasterizedIcon(path = ORIGINAL_PATH,
                            expUIPath = expUIPath,
                            classLoader = classLoader,
                            cacheKey = ORIGINAL_CACHE_KEY,
                            flags = ORIGINAL_FLAGS,
                            expUICacheKey = expUICacheKey,
                            expUIFlags = expUIFlags,
                            toolTip = null) as CachedImageIcon
}

private fun loadIconWithoutExpUIData(expUIPath: String): CachedImageIcon {
  return loadRasterizedIcon(path = ORIGINAL_PATH,
                            expUIPath = expUIPath,
                            classLoader = classLoader,
                            cacheKey = ORIGINAL_CACHE_KEY,
                            flags = ORIGINAL_FLAGS,
                            toolTip = null) as CachedImageIcon
}

/**
 * Loads exactly the [path] file in the light variant.
 */
private fun loadFileIcon(path: String): Icon = (IconLoader.getIcon("/$path", classLoader) as CachedImageIcon).getDarkIcon(false)

private fun Icon.pixels(): IntArray {
  val image = BufferedImage(iconWidth, iconHeight, BufferedImage.TYPE_INT_ARGB)
  val g = image.createGraphics()
  try {
    paintIcon(null, g, 0, 0)
  }
  finally {
    g.dispose()
  }
  return image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
}
