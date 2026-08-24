// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import com.intellij.util.concurrency.annotations.RequiresEdt

internal const val STATISTICS_BUCKET_MS = 500L
internal const val STATISTICS_BUCKET_COUNT = 10

internal data class CacheHitRate(val hits: Int, val misses: Int) {
  val hitPercent: Int get() = ((hits.toLong() * 100 + (hits + misses) / 2) / (hits + misses)).toInt()
}

internal object EditorAnimationCacheStatistics {
  private val hits = IntArray(STATISTICS_BUCKET_COUNT)
  private val misses = IntArray(STATISTICS_BUCKET_COUNT)
  private val stamps = LongArray(STATISTICS_BUCKET_COUNT) { Long.MIN_VALUE }

  @RequiresEdt
  fun recordHit(): Boolean {
    hits[bucketAt(currentStamp())]++
    return true
  }

  @RequiresEdt
  fun recordMiss(): Boolean {
    misses[bucketAt(currentStamp())]++
    return false
  }

  @RequiresEdt
  fun hitRate(): CacheHitRate? {
    val newest = currentStamp()
    val oldest = newest - STATISTICS_BUCKET_COUNT + 1

    var totalHits = 0
    var totalMisses = 0
    for (bucket in 0 until STATISTICS_BUCKET_COUNT) {
      if (stamps[bucket] in oldest..newest) {
        totalHits += hits[bucket]
        totalMisses += misses[bucket]
      }
    }

    return when (totalHits + totalMisses) {
      0 -> null
      else -> CacheHitRate(totalHits, totalMisses)
    }
  }

  private fun currentStamp(): Long = System.nanoTime() / (STATISTICS_BUCKET_MS * 1_000_000)

  private fun bucketAt(stamp: Long): Int {
    val bucket = Math.floorMod(stamp, STATISTICS_BUCKET_COUNT.toLong()).toInt()
    if (stamps[bucket] != stamp) {
      stamps[bucket] = stamp
      hits[bucket] = 0
      misses[bucket] = 0
    }
    return bucket
  }
}
