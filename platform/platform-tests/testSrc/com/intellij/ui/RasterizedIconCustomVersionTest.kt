// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.IconPathPatcher
import com.intellij.openapi.util.findIconCustomVersion
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.ImageDescriptor
import com.intellij.ui.icons.decodeCachedImageIconFromByteArray
import com.intellij.ui.icons.findIconByPath
import com.intellij.ui.icons.loadRasterizedIcon
import com.intellij.ui.svg.SvgCacheManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.GraphicsEnvironment
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.swing.Icon

/**
 * Checks the custom versions of the icons with the custom version flags, such as `name@20x20.svg` for `name.svg`.
 */
@TestApplication
class RasterizedIconCustomVersionTest {
  @BeforeEach
  fun setUp() {
    IconLoader.activate()
    IconLoader.clearCacheInTests()
  }

  @AfterEach
  fun tearDown() {
    if (GraphicsEnvironment.isHeadless()) {
      IconLoader.deactivate()
    }
  }

  @Test
  fun `flags select the custom version file`() {
    val version = findIconCustomVersion(icon = loadIcon(flags = FLAGS_WITH_20x20), width = 20, height = 20) as CachedImageIcon
    // a version that the flags give has the flags of its own file
    assertThat(version.imageFlags).isEqualTo(ImageDescriptor.HAS_DARK)
    assertThat(version.getDarkIcon(false).pixels()).isEqualTo(loadFileIcon(VERSION_PATH).pixels())
    assertThat(version.getDarkIcon(true).pixels()).isEqualTo(loadFileIcon(VERSION_DARK_PATH).pixels())
  }

  @Test
  fun `New UI flags select the custom version file`() {
    assumeTrue(NewUiValue.isEnabled(), "the New UI is off")
    val icon = loadRasterizedIcon(path = ORIGINAL_PATH,
                                  expUIPath = PATH,
                                  classLoader = classLoader,
                                  cacheKey = ORIGINAL_CACHE_KEY,
                                  flags = 0,
                                  expUICacheKey = CACHE_KEY,
                                  expUIFlags = FLAGS_WITH_20x20,
                                  toolTip = null) as CachedImageIcon
    val version = findIconCustomVersion(icon = icon, width = 20, height = 20) as CachedImageIcon
    assertThat(version.imageFlags).isEqualTo(ImageDescriptor.HAS_DARK)
    assertThat(version.getDarkIcon(true).pixels()).isEqualTo(loadFileIcon(VERSION_DARK_PATH).pixels())
  }

  @Test
  fun `flags tell that the custom version is missing`() {
    // the file exists, but the flags tell the opposite, so no lookup occurs
    assertThat(findIconCustomVersion(icon = loadIcon(flags = ImageDescriptor.HAS_CUSTOM_VERSION_DATA), width = 20, height = 20)).isNull()
  }

  @Test
  fun `path patcher gives a custom version that the flags tell is missing`() {
    val patcher = object : IconPathPatcher() {
      override fun patchPath(path: String, classLoader: ClassLoader?): String? {
        return if (path == "/$MISSING_VERSION_PATH") "/$VERSION_PATH" else null
      }
    }
    IconLoader.installPathPatcher(patcher)
    try {
      val icon = loadIcon(flags = ImageDescriptor.HAS_CUSTOM_VERSION_DATA)
      val version = findIconCustomVersion(icon = icon, width = 14, height = 14) as CachedImageIcon
      assertThat(version.getDarkIcon(false).pixels()).isEqualTo(loadFileIcon(VERSION_PATH).pixels())
    }
    finally {
      IconLoader.removePathPatcher(patcher)
    }
  }

  @Test
  fun `custom version without the flag data is found by its path`() {
    val version = findIconCustomVersion(icon = loadIcon(flags = 0), width = 20, height = 20) as CachedImageIcon
    // the version that the lookup by path finds has no flags
    assertThat(version.imageFlags).isEqualTo(0)
    assertThat(version.getDarkIcon(false).pixels()).isEqualTo(loadFileIcon(VERSION_PATH).pixels())
  }

  @Test
  fun `custom version with a size that the flags do not cover is found by its path`() {
    val version = findIconCustomVersion(icon = loadIcon(flags = FLAGS_WITH_20x20), width = 24, height = 24) as CachedImageIcon
    assertThat(version.imageFlags).isEqualTo(0)
  }

  @Test
  @SystemProperty(propertyKey = "idea.ui.icons.svg.disk.cache", propertyValue = "true")
  fun `custom version has its own image under the cache key of the icon`(@TempDir dir: Path) {
    val cache = timeoutRunBlocking { SvgCacheManager.createSvgCacheManager(dir.resolve("icon-cache.db"))!! }
    val previousCache = SvgCacheManager.svgCache
    SvgCacheManager.svgCache = cache
    try {
      val icon = loadIcon(flags = FLAGS_WITH_20x20).getDarkIcon(false)
      val iconPixels = icon.pixels()
      val version = findIconCustomVersion(icon = icon, width = 20, height = 20) as CachedImageIcon
      val versionPixels = loadFileIcon(VERSION_PATH).pixels()
      assertThat(versionPixels).isNotEqualTo(iconPixels)
      assertThat(version.getDarkIcon(false).pixels()).isEqualTo(versionPixels)

      // the decoded version must not take the image of the icon from the disk cache
      val decoded = decodeCachedImageIconFromByteArray(version.encodeToByteArray()!!) as CachedImageIcon
      assertThat(decoded.getDarkIcon(false).pixels()).isEqualTo(versionPixels)
    }
    finally {
      SvgCacheManager.svgCache = previousCache
      cache.close()
    }
  }
}

private const val PATH = "expui/general/add.svg"
private const val VERSION_PATH = "expui/general/add@20x20.svg"
private const val VERSION_DARK_PATH = "expui/general/add@20x20_dark.svg"
private const val MISSING_VERSION_PATH = "expui/general/add@14x14.svg"
private const val CACHE_KEY = 42
private const val ORIGINAL_PATH = "rasterizedIconCustomVersionTest/original.svg"
private const val ORIGINAL_CACHE_KEY = 11
private const val FLAGS_WITH_20x20 = ImageDescriptor.HAS_DARK or ImageDescriptor.HAS_CUSTOM_VERSION_DATA or
  ImageDescriptor.HAS_20x20 or ImageDescriptor.HAS_20x20_DARK

private val classLoader: ClassLoader
  get() = RasterizedIconCustomVersionTest::class.java.classLoader

private fun loadIcon(flags: Int): CachedImageIcon {
  return loadRasterizedIcon(path = PATH,
                            expUIPath = null,
                            classLoader = classLoader,
                            cacheKey = CACHE_KEY,
                            flags = flags,
                            toolTip = null) as CachedImageIcon
}

/**
 * Loads exactly the [path] file in the light variant. The icon is not in the icon cache, so it cannot be a version under the test.
 */
private fun loadFileIcon(path: String): Icon {
  return (findIconByPath(path = path, classLoader = classLoader, cache = null) as CachedImageIcon).getDarkIcon(false)
}

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
