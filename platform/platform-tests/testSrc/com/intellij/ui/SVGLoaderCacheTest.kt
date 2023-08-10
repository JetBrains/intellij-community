// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UndesirableClassUsage")

package com.intellij.ui

import com.intellij.ui.scale.paint.ImageComparator
import com.intellij.ui.scale.paint.ImageComparator.AASmootherComparator
import com.intellij.ui.svg.SvgCacheClassifier
import com.intellij.ui.svg.SvgCacheManager
import com.intellij.ui.svg.createIconCacheKey
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Path

class SVGLoaderCacheTest {
  @Test
  fun noEntry(@TempDir dir: Path) = runBlocking {
    val cache = createCache(dir)
    try {
      assertThat(cache.loadFromCache(longArrayOf(42))).isNull()
    }
    finally {
      cache.close()
    }
  }

  @Test
  fun saveAndLoad(@TempDir dir: Path) = runBlocking {
    var cache = createCache(dir)
    try {
      val i = BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)
      i.setRGB(0, 0, 0xff00ff)
      i.setRGB(0, 1, 0x00ff00)
      val imageBytes = byteArrayOf(1, 2, 3)
      val theme = 0L
      val svgCacheClassifier = SvgCacheClassifier(1f)
      cache.storeLoadedImage(createIconCacheKey(imageBytes = imageBytes, compoundKey = svgCacheClassifier, themeKey = 0), i)
      cache.close()
      cache = createCache(dir)
      val copy = cache.loadFromCache(createIconCacheKey(imageBytes = imageBytes, themeKey = theme, compoundKey = svgCacheClassifier))!!
      assertThat(copy.width).isEqualTo(10)
      assertThat(copy.height).isEqualTo(10)
      ImageComparator.compareAndAssert(AASmootherComparator(0.1, 0.1, Color(0, 0, 0, 0)), i, copy, null)
      assertThat(cache.loadFromCache((createIconCacheKey(imageBytes, SvgCacheClassifier(1f, false, false), 123)))).isNull()
      assertThat(cache.loadFromCache(createIconCacheKey(byteArrayOf(6, 7), SvgCacheClassifier(1f, false, false), theme))).isNull()
      assertThat(cache.loadFromCache(createIconCacheKey(imageBytes, SvgCacheClassifier(2f, false, false), theme))).isNull()
    }
    finally {
      cache.close()
    }
  }
}

private suspend fun createCache(dir: Path): SvgCacheManager {
  return SvgCacheManager.createSvgCacheManager(dir.resolve("db.db"))!!
}

