// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<FrontendViewportDataCache<*>>()

private const val WINDOW_LINES_COUNT = 100

internal class FrontendViewportDataCache<T>(
  private val loadData: suspend (firstIndex: Int, lastIndexInclusive: Int) -> List<T>?,
  private val warnOnFailedUpdate: Boolean = true,
) {
  @Volatile
  private var windowCache: StampedWindowCache<T>? = null

  private val perLineCacheMutex = Mutex()

  @Volatile
  private var perLineCache: StampedLinesCache<T>? = null

  suspend fun update(viewport: ViewportInfo, lastPossibleIndex: Int, currentStamp: Long) {
    if (lastPossibleIndex < 0) {
      windowCache = null
      return
    }
    val currentCache = windowCache
    if (currentCache == null || currentCache.shouldBeUpdated(viewport, currentStamp)) {
      val (firstIndex, lastIndex) = indicesToLoad(viewport, lastPossibleIndex)
      // TODO: we may optimize it more by reusing already calculated lines,
      //   since now we recalculate full viewport when indices to load are changed even a bit.
      val newData = loadData(firstIndex, lastIndex)
      if (newData != null) {
        windowCache = StampedWindowCache(currentStamp, firstIndex, lastIndex, newData)
      }
      else {
        windowCache = null
        if (warnOnFailedUpdate) {
          LOG.warn("Cannot update cache[$this] for viewport ($viewport)")
        }
      }
    }
  }

  suspend fun getDataWithCaching(index: Int, currentStamp: Long): T? {
    val currentData = getData(index, currentStamp)
    if (currentData != null) {
      return currentData
    }

    return cacheLine(index, currentStamp)
  }

  suspend fun cacheLine(index: Int, currentStamp: Long, ): T? {
    val lineCache = perLineCacheMutex.withLock {
      val currentLinesCache = perLineCache
      if (currentLinesCache == null || currentLinesCache.shouldBeUpdated(currentStamp)) {
        val newCache = StampedLinesCache<T>(currentStamp)
        perLineCache = newCache
        return@withLock newCache
      }
      currentLinesCache
    }

    val lineData = loadData(index, index)?.firstOrNull() ?: return null
    lineCache.updateLine(index, lineData)
    return lineData
  }

  fun getData(index: Int, currentStamp: Long): T? {
    val lineData = perLineCache?.getDataForIndex(index, currentStamp)
    if (lineData != null) {
      return lineData
    }
    return windowCache?.getDataForIndex(index, currentStamp)
  }

  fun clear() {
    windowCache = null
    perLineCache = null
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

  private class StampedLinesCache<T>(
    private val modificationStamp: Long,
  ) {
    private val linesCache = ConcurrentHashMap<Int, T>()

    fun updateLine(line: Int, data: T) {
      linesCache[line] = data
    }

    fun getDataForIndex(index: Int, currentStamp: Long): T? {
      if (modificationStamp != currentStamp) {
        return null
      }
      return linesCache[index]
    }

    fun shouldBeUpdated(currentStamp: Long): Boolean {
      return modificationStamp != currentStamp
    }
  }

  private class StampedWindowCache<T>(
    private val modificationStamp: Long,
    private val firstIndex: Int,
    private val lastIndex: Int,
    private val data: List<T>,
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
      return data[index - firstIndex]
    }
  }
}