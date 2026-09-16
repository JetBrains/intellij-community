// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal val STATISTICS_BUCKET_DURATION: Duration = 500.milliseconds
internal const val STATISTICS_BUCKET_COUNT = 10

internal object EditorAnimationCacheStatistics {
  private val startedAt = AnimationClock.markAnimationNow()
  private val hits = IntArray(STATISTICS_BUCKET_COUNT)
  private val misses = IntArray(STATISTICS_BUCKET_COUNT)
  private val stamps = arrayOfNulls<AnimationTimeMark>(STATISTICS_BUCKET_COUNT)

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun recordHit(): Boolean {
    hits[bucketAt()]++
    return true
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun recordMiss(): Boolean {
    misses[bucketAt()]++
    return false
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun hitRate(): CacheHitRate? {
    val elapsedBuckets = currentBucket()
    val newest = currentStamp(elapsedBuckets)
    val windowLength = STATISTICS_BUCKET_DURATION * (STATISTICS_BUCKET_COUNT - 1)
    val oldest = newest - windowLength
    var totalHits = 0
    var totalMisses = 0
    for (bucket in 0 until STATISTICS_BUCKET_COUNT) {
      val stamp = stamps[bucket]
      if (stamp != null && stamp in oldest..newest) {
        totalHits += hits[bucket]
        totalMisses += misses[bucket]
      }
    }
    return if (totalHits + totalMisses == 0) {
      null
    } else {
      CacheHitRate(totalHits, totalMisses)
    }
  }

  private fun currentBucket(): Long {
    val elapsed = startedAt.elapsedNow()
    return (elapsed / STATISTICS_BUCKET_DURATION).toLong()
  }

  private fun currentStamp(elapsedBuckets: Long): AnimationTimeMark {
    val elapsed = STATISTICS_BUCKET_DURATION * elapsedBuckets.toDouble()
    return startedAt + elapsed
  }

  /**
   * The bucket the current moment falls in, reset first if it still holds counts from an older window.
   */
  private fun bucketAt(): Int {
    val elapsedBuckets = currentBucket()
    val bucket = (elapsedBuckets % STATISTICS_BUCKET_COUNT).toInt()
    val stamp = currentStamp(elapsedBuckets)
    if (stamps[bucket] != stamp) {
      stamps[bucket] = stamp
      hits[bucket] = 0
      misses[bucket] = 0
    }
    return bucket
  }
}
