// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.concurrent.atomic.LongAdder

@ApiStatus.Internal
object IconLoadMeasurer {
  @JvmField
  val svgDecoding: Counter = Counter("svg-decode")
  private val svgLoading = Counter("svg-load")
  @JvmField
  val svgCacheWrite: Counter = Counter("svg-cache-write")
  @JvmField
  val svgCacheRead: Counter = Counter("svg-cache-read")
  @JvmField
  val pngDecoding: Counter = Counter("png-decode")

  private val pngLoading = Counter("png-load")

  @JvmField
  val findIcon: Counter = Counter("find-icon")
  @JvmField
  val findIconLoad: Counter = Counter("find-icon-load")
  @JvmField
  val loadFromUrl: Counter = Counter("load-from-url")
  @JvmField
  val loadFromResources: Counter = Counter("load-from-resource")

  /**
   * Get icon for action. Measured to understand the impact.
   */
  @JvmField
  val actionIcon: Counter = Counter("action-icon")

  val stats: List<Counter>
    get() {
      return listOf(findIcon, findIconLoad,
                    loadFromUrl, loadFromResources,
                    svgLoading, svgDecoding, svgCacheRead, svgCacheWrite,
                    pngLoading, pngDecoding,
                    actionIcon)
    }

  fun addLoading(isSvg: Boolean, start: Long) {
    (if (isSvg) svgLoading else pngLoading).end(start)
  }

  class Counter internal constructor(@JvmField val name: @NonNls String) {
    private val counter = LongAdder()
    private val totalDuration = LongAdder()

    val count: Int
      get() = counter.sum().toInt()

    fun getTotalDuration(): Long = totalDuration.sum()

    fun end(startTime: Long) {
      if (startTime > 0) {
        val duration = System.nanoTime() - startTime
        counter.increment()
        totalDuration.add(duration)
      }
    }
  }
}