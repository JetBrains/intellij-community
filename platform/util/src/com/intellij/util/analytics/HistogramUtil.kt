/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("HistogramUtil")

package com.intellij.util.analytics

import com.google.wireless.android.sdk.stats.HistogramBin
import org.HdrHistogram.Histogram
import org.HdrHistogram.HistogramIterationValue

typealias HistogramProto = com.google.wireless.android.sdk.stats.Histogram

/**
 * Returns the inclusive start value for the given bin.
 */
val HistogramIterationValue.start: Long get() {
  // Special case: HdrHistogram encodes the first bin as 0,0 even though it's supposed to be 0,1
  if (valueIteratedFrom == 0L && valueIteratedTo == 0L) {
    return 0L
  }
  return valueIteratedFrom + 1
}

/**
 * Returns the exclusive end value for the given bin.
 */
val HistogramIterationValue.end: Long get() {
  return valueIteratedTo + 1
}

/**
 * Converts a [Histogram] to a proto.
 */
fun Histogram.toProto(): HistogramProto {
  val builder = HistogramProto.newBuilder()

  var total = totalCount
  builder.totalCount = total
  for (value in allValues()) {
    if (value.countAddedInThisIterationStep > 0L) {
      // HdrHistogram has a special case for it's first bin, which uses the range 0 to 0. Subsequent bins have an inclusive
      // upper bound and exclusive lower bound. We use inclusive lower bounds and exclusive upper bounds in the proto, so
      // need to shuffle around some indices here.
      builder.addBin(HistogramBin.newBuilder()
                       .setStart(value.start)
                       .setEnd(value.end)
                       .setTotalSamples(total)
                       .setSamples(value.countAddedInThisIterationStep))
    }
    total -= value.countAddedInThisIterationStep
  }

  return builder.build()
}
