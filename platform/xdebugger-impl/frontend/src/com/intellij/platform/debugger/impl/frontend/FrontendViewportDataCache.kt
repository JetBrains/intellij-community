// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.diagnostic.logger

private val LOG = logger<FrontendViewportDataCache<*>>()

private const val WINDOW_LINES_COUNT = 100

internal class FrontendViewportDataCache<T>(
  private val loadData: suspend (firstIndex: Int, lastIndexInclusive: Int) -> List<T>?,
  private val warnOnFailedUpdate: Boolean = true,
) {
  @Volatile
  private var cache: StampedCache<T>? = null

  suspend fun update(viewport: ViewportInfo, lastPossibleIndex: Int, currentStamp: Long) {
    if (lastPossibleIndex < 0) {
      cache = null
      return
    }
    val currentCache = cache
    if (currentCache == null || currentCache.shouldBeUpdated(viewport, currentStamp)) {
      val (firstIndex, lastIndex) = indicesToLoad(viewport, lastPossibleIndex)
      // TODO: we may optimize it more by reusing already calculated lines,
      //   since now we recalculate full viewport when indices to load are changed even a bit.
      val newData = loadData(firstIndex, lastIndex)
      if (newData != null) {
        cache = StampedCache(currentStamp, firstIndex, lastIndex, newData)
      }
      else {
        cache = null
        if (warnOnFailedUpdate) {
          LOG.warn("Cannot update cache[$this] for viewport ($viewport)")
        }
      }
    }
  }

  fun getData(index: Int, currentStamp: Long): T? {
    return cache?.getDataForIndex(index, currentStamp)
  }

  fun clear() {
    cache = null
  }

  // both indices are inclusive
  private fun indicesToLoad(viewport: ViewportInfo, lastPossibleIndex: Int): Pair<Int, Int> {
    val firstVisibleIndex = viewport.firstVisibleIndex
    val lastVisibleIndex = viewport.lastVisibleIndexInclusive
    val firstIndex = ((firstVisibleIndex / WINDOW_LINES_COUNT - 2) * WINDOW_LINES_COUNT).coerceIn(0, lastPossibleIndex)
    val lastIndex = ((lastVisibleIndex / WINDOW_LINES_COUNT + 2) * WINDOW_LINES_COUNT).coerceIn(0, lastPossibleIndex)
    return firstIndex to lastIndex
  }

  internal data class ViewportInfo(
    val firstVisibleIndex: Int,
    val lastVisibleIndexInclusive: Int,
  )

  private class StampedCache<T>(
    private val modificationStamp: Long,
    private val firstIndex: Int,
    private val lastIndex: Int,
    private val types: List<T>,
  ) {
    fun shouldBeUpdated(viewport: ViewportInfo, currentStamp: Long): Boolean {
      return modificationStamp != currentStamp || firstIndex != viewport.firstVisibleIndex || lastIndex != viewport.lastVisibleIndexInclusive
    }

    fun getDataForIndex(index: Int, currentStamp: Long): T? {
      if (modificationStamp != currentStamp) {
        return null
      }
      if (index !in firstIndex..lastIndex) {
        return null
      }
      return types[index - firstIndex]
    }
  }
}