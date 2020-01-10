// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.uploader

import com.intellij.internal.statistic.StatisticsEventLogUtil
import com.intellij.testFramework.UsefulTestCase

class EventLogUtilTest : UsefulTestCase() {

  private fun doTestMergeArray(a1: Array<String>, a2: Array<String>, expected: Array<String>) {
    val actual = StatisticsEventLogUtil.mergeArrays(a1, a2)

    assertEquals(expected.size, actual.size)
    for ((index, s) in actual.withIndex()) {
      assertEquals(expected[index], actual[index])
    }
  }

  fun test_merge_empty_arrays() {
    doTestMergeArray(arrayOf(), arrayOf(), arrayOf())
  }

  fun test_merge_first_empty_array() {
    doTestMergeArray(arrayOf(), arrayOf("A"), arrayOf("A"))
  }

  fun test_merge_second_empty_array() {
    doTestMergeArray(arrayOf("A"), arrayOf(), arrayOf("A"))
  }

  fun test_merge_arrays() {
    doTestMergeArray(arrayOf("A"), arrayOf("B"), arrayOf("A", "B"))
  }

  fun test_merge_long_arrays() {
    doTestMergeArray(arrayOf("A", "B", "C"), arrayOf("D", "E", "F", "G"),
                     arrayOf("A", "B", "C", "D", "E", "F", "G"))
    doTestMergeArray(arrayOf("A", "B", "C", "X", "Y", "Z"), arrayOf("D", "E", "F", "G"),
                     arrayOf("A", "B", "C", "X", "Y", "Z", "D", "E", "F", "G"))
  }
}
