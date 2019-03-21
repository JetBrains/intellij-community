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
package com.intellij.util.analytics

import org.HdrHistogram.Histogram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistogramUtilTest {
  @Test
  fun testBinBoundaries() {
    // We repeat the test 100 times to verify bins with different numbers of digits
    for (i in 0..100) {
      val hist = Histogram(1)
      hist.recordValue(i.toLong())
      val proto = hist.toProto()
      assertEquals("There should be one sample in the histogram", 1, proto.totalCount)
      val binList = proto.binList
      assertEquals("Empty bins should not be converted to protos", 1, binList.size)
      val bin = binList[0]
      assertTrue("The bin start value should be inclusive", bin.start <= i)
      assertTrue("The bin end value should be exclusive", i < bin.end)
    }
  }

  @Test
  fun testEmptyHistogram() {
    val emptyProto = Histogram(1).toProto()
    assertEquals("There should be no samples in an empty histogram", 0, emptyProto.totalCount)
  }
}