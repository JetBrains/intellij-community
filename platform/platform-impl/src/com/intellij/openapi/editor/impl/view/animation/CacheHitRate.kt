// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

internal data class CacheHitRate(val hits: Int, val misses: Int) {
  val hitPercent: Int
    get() {
      val total = hits + misses
      val rounding = total / 2
      val scaledHits = hits.toLong() * 100
      val roundedPercent = (scaledHits + rounding) / total
      return roundedPercent.toInt()
    }
}
