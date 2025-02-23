// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class FusHistogramBuilder(
  private val bucketValues: LongArray,
  private val roundingDirection: RoundingDirection = RoundingDirection.DOWN
) {
  val buckets: IntArray = IntArray(bucketValues.size)

  fun addValue(value: Long) {
    var bucketIndex = bucketValues.indexOfFirst { it > value }
    if (bucketIndex != -1) {
      // We found a bucket that needs to be incremented
      if (bucketIndex > 0 && roundingDirection == RoundingDirection.DOWN) {
        // The value falls into the previous bucket
        bucketIndex--
      }
    } else {
      // The value is greater than all buckets, it falls into the last one
      bucketIndex = bucketValues.size - 1
    }
    buckets[bucketIndex]++
  }

  fun build(): FusHistogram {
    return FusHistogram(buckets)
  }

  enum class RoundingDirection { DOWN, UP }
}

@Internal
class FusHistogram(
  val buckets: IntArray
)