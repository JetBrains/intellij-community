// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.diagnostic.StartUpMeasurer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.concurrent.atomic.LongAdder

@ApiStatus.Internal
object IconLoadMeasurer {
  @JvmField
  val svgDecoding = Counter("svg-decode")
  private val svgLoading = Counter("svg-load")
  @JvmField
  val svgPreBuiltLoad = Counter("svg-prebuilt")
  @JvmField
  val svgCacheWrite = Counter("svg-cache-write")
  @JvmField
  val svgCacheRead = Counter("svg-cache-read")
  @JvmField
  val pngDecoding = Counter("png-decode")

  private val pngLoading = Counter("png-load")

  @JvmField
  val findIcon = Counter("find-icon")
  @JvmField
  val findIconLoad = Counter("find-icon-load")
  @JvmField
  val loadFromUrl = Counter("load-from-url")
  @JvmField
  val loadFromResources = Counter("load-from-resource")

  /**
   * Get icon for action. Measured to understand impact.
   */
  @JvmField
  val actionIcon: Counter = Counter("action-icon")

  val stats: List<Counter>
    get() {
      return listOf(findIcon, findIconLoad,
                    loadFromUrl, loadFromResources,
                    svgLoading, svgDecoding, svgPreBuiltLoad, svgCacheRead, svgCacheWrite,
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
        val duration = StartUpMeasurer.getCurrentTime() - startTime
        counter.increment()
        totalDuration.add(duration)
      }
    }
  }
}