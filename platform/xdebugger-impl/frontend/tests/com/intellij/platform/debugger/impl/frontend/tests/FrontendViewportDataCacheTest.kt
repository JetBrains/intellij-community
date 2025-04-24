// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.tests

import com.intellij.platform.debugger.impl.frontend.FrontendViewportDataCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

internal class FrontendViewportDataCacheTest {
  private fun createSimpleDataIdentityCache(): FrontendViewportDataCache<Int> {
    return FrontendViewportDataCache(
      loadData = { firstIndex, lastIndexInclusive ->
        (firstIndex..lastIndexInclusive).toList()
      }
    )
  }

  @Test
  fun `test viewport covers all content`() = runBlocking {
    val cache = createSimpleDataIdentityCache()
    val stamp = 0L
    cache.update(FrontendViewportDataCache.ViewportInfo(0, 10), 10, stamp)
    for (i in 0..10) {
      assertEquals(i, cache.getData(i, stamp))
    }
  }

  @Test
  fun `test viewport smaller than content, all viewport info should be loaded`() = runBlocking {
    val cache = createSimpleDataIdentityCache()
    val stamp = 0L
    val viewport = FrontendViewportDataCache.ViewportInfo(250, 552)
    cache.update(viewport, 1200, stamp)
    for (i in viewport.firstVisibleIndex..viewport.lastVisibleIndexInclusive) {
      assertEquals(i, cache.getData(i, stamp))
    }
  }

  @Test
  fun `test stamp changed, so no data should be returned from cache`() = runBlocking {
    val cache = createSimpleDataIdentityCache()
    val stamp = 0L
    val viewport = FrontendViewportDataCache.ViewportInfo(250, 552)
    cache.update(viewport, 1200, stamp)
    val newStamp = 100L
    for (i in viewport.firstVisibleIndex..viewport.lastVisibleIndexInclusive) {
      assertNull(cache.getData(i, newStamp))
    }
  }

  @Test
  fun `test viewport changed significantly, new viewport is available, old viewport is not available`() = runBlocking {
    val cache = createSimpleDataIdentityCache()
    val stamp = 0L
    val viewportOld = FrontendViewportDataCache.ViewportInfo(250, 552)
    cache.update(viewportOld, 5000, stamp)
    for (i in viewportOld.firstVisibleIndex..viewportOld.lastVisibleIndexInclusive) {
      assertEquals(i, cache.getData(i, stamp))
    }

    val viewportNew = FrontendViewportDataCache.ViewportInfo(4250, 4552)
    cache.update(viewportNew, 5000, stamp)
    for (i in viewportNew.firstVisibleIndex..viewportNew.lastVisibleIndexInclusive) {
      assertEquals(i, cache.getData(i, stamp))
    }

    for (i in viewportOld.firstVisibleIndex..viewportOld.lastVisibleIndexInclusive) {
      assertNull(cache.getData(i, stamp))
    }
  }


  @Test
  fun `test small change in viewport doesn't change cache`() = runBlocking {
    val cache = createSimpleDataIdentityCache()
    val stamp = 0L
    val viewportOld = FrontendViewportDataCache.ViewportInfo(250, 552)
    cache.update(viewportOld, 5000, stamp)
    val cachedLines = mutableListOf<Int>()
    for (i in 0..5000) {
      val cachedData = cache.getData(i, stamp) ?: continue
      cachedLines.add(cachedData)
    }

    val viewportNew = FrontendViewportDataCache.ViewportInfo(250, 553)
    cache.update(viewportNew, 5000, stamp)
    val cachedLinesNew = mutableListOf<Int>()
    for (i in 0..5000) {
      val cachedData = cache.getData(i, stamp) ?: continue
      cachedLinesNew.add(cachedData)
    }

    assertEquals(cachedLines, cachedLinesNew)
  }

  @Test
  fun `test first and last indices in huge list, huge viewport`() = runBlocking {
    val cache = createSimpleDataIdentityCache()
    val stamp = 0L
    val viewportStartList = FrontendViewportDataCache.ViewportInfo(0, 20_000)
    cache.update(viewportStartList, 50_000, stamp)
    for (i in 0..20_000) {
      assertEquals(i, cache.getData(i, stamp))
    }

    val viewportEndList = FrontendViewportDataCache.ViewportInfo(20_000, 50_000)
    cache.update(viewportEndList, 50_000, stamp)
    for (i in viewportEndList.firstVisibleIndex..50_000) {
      assertEquals(i, cache.getData(i, stamp))
    }
  }

  @Test
  fun `test cache is empty for negative last possible index`() = runBlocking {
    val cache = createSimpleDataIdentityCache()
    val stamp = 0L
    val viewportStartList = FrontendViewportDataCache.ViewportInfo(0, 0)
    cache.update(viewportStartList, -1, stamp)
    for (i in 0..20_000) {
      assertNull(cache.getData(i, stamp))
    }
  }
}