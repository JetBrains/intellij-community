// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.events

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class FusHistogramTest {
  @Test
  fun test() {
    testHist_0_10_50(1, 0, 0) {
      addValue(1)
    }
    testHist_0_10_50(0, 1, 0) {
      addValue(14)
    }
    testHist_0_10_50(0, 0, 1) {
      addValue(50)
    }
    testHist_0_10_50(0, 0, 1) {
      addValue(100)
    }
    testHist_0_10_50(1, 2, 1) {
      addValue(100)
      addValue(15)
      addValue(5)
      addValue(12)
    }
    // this situation (out of range) should not happen, it is UB
    testHist_0_10_50(1, 0, 0) {
      addValue(-10)
    }
  }

  fun testHist_0_10_50(vararg buckets: Int, b: FusHistogramBuilder.() -> Unit) {
    val builder = FusHistogramBuilder(longArrayOf(0, 10, 50))
    b(builder)
    Assertions.assertArrayEquals(buckets, builder.build().buckets)
  }
}