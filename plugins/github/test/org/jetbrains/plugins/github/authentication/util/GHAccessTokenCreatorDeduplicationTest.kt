// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.util

import org.junit.Assert.assertEquals
import org.junit.Test

class GHAccessTokenCreatorDeduplicationTest {
  @Test
  fun testEmptyList() {
    val index = GHAccessTokenCreator.findNextDeduplicationIndex("test", listOf())
    assertEquals(0, index)
  }

  @Test
  fun testGoodList() {
    val index = GHAccessTokenCreator.findNextDeduplicationIndex("test", listOf("test", "test_1", "test_2"))
    assertEquals(3, index)
  }

  @Test
  fun testZeroConversion() {
    val index = GHAccessTokenCreator.findNextDeduplicationIndex("test", listOf("test_1", "test_2"))
    assertEquals(0, index)
  }

  @Test
  fun testGoodListWithMultipleDigits() {
    val list = mutableListOf("test")
    for (i in 1..99)
      list += "test_$i"

    val index = GHAccessTokenCreator.findNextDeduplicationIndex("test", list)
    assertEquals(100, index)
  }

  @Test
  fun testGoodListWithDifferentCase() {
    val index = GHAccessTokenCreator.findNextDeduplicationIndex("test", listOf("TEst", "tESt_1", "teST_2"))
    assertEquals(3, index)
  }

  @Test
  fun testGoodListWithDifferentCaseDuplicates() {
    val index = GHAccessTokenCreator.findNextDeduplicationIndex("test", listOf("TEst", "tESt_1", "teST_1", "TesT_1", "test_2", "TEST_2"))
    assertEquals(3, index)
  }

  @Test
  fun testSkipList() {
    val index = GHAccessTokenCreator.findNextDeduplicationIndex("test", listOf("test", "test_2"))
    assertEquals(1, index)
  }

  @Test
  fun testListWithFault() {
    val index = GHAccessTokenCreator.findNextDeduplicationIndex("test", listOf("test", "test _1"))
    assertEquals(1, index)
  }
}