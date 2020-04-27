// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.utils.StatisticsUtil.getNextPowerOfTwo
import com.intellij.internal.statistic.utils.getCountingStepName
import com.intellij.internal.statistic.utils.getCountingUsage
import org.junit.Test
import kotlin.test.assertEquals

class StatisticsUtilTest {
  @Test
  fun test_counting_usage() {
    val steps = listOf(0, 1, 2, 10, 1000, 10 * 1000, 1000 * 1000)
    assertCountingUsage("test.value.count.0", 0, steps)
    assertCountingUsage("test.value.count.1", 1, steps)
    assertCountingUsage("test.value.count.2+", 2, steps)
    assertCountingUsage("test.value.count.2+", 3, steps)
    assertCountingUsage("test.value.count.2+", 9, steps)
    assertCountingUsage("test.value.count.10+", 10, steps)
    assertCountingUsage("test.value.count.10+", 500, steps)
    assertCountingUsage("test.value.count.1K+", 1000, steps)
    assertCountingUsage("test.value.count.10K+", 200 * 1000, steps)
    assertCountingUsage("test.value.count.10K+", 999999, steps)
    assertCountingUsage("test.value.count.1M+", 1000 * 1000, steps)
    assertCountingUsage("test.value.count.1M+", 2000 * 1000, steps)
  }

  @Test
  fun test_infinite_counting_usage() {
    assertInfiniteCountingUsage("test.value.count.<0", -1000)
    assertInfiniteCountingUsage("test.value.count.<0", -1)
    assertInfiniteCountingUsage("test.value.count.0", 0)
    assertInfiniteCountingUsage("test.value.count.1", 1)
    assertInfiniteCountingUsage("test.value.count.2", 2)
    assertInfiniteCountingUsage("test.value.count.3+", 3)
    assertInfiniteCountingUsage("test.value.count.3+", 4)
    assertInfiniteCountingUsage("test.value.count.5+", 5)
    assertInfiniteCountingUsage("test.value.count.5+", 6)
    assertInfiniteCountingUsage("test.value.count.5+", 7)
    assertInfiniteCountingUsage("test.value.count.5+", 9)
    assertInfiniteCountingUsage("test.value.count.10+", 10)
    assertInfiniteCountingUsage("test.value.count.10+", 14)
    assertInfiniteCountingUsage("test.value.count.15+", 15)
    assertInfiniteCountingUsage("test.value.count.15+", 29)
    assertInfiniteCountingUsage("test.value.count.30+", 30)
    assertInfiniteCountingUsage("test.value.count.30+", 49)
    assertInfiniteCountingUsage("test.value.count.50+", 50)
    assertInfiniteCountingUsage("test.value.count.50+", 99)
    assertInfiniteCountingUsage("test.value.count.100+", 100)
    assertInfiniteCountingUsage("test.value.count.100+", 341)
    assertInfiniteCountingUsage("test.value.count.1K+", 1000)
    assertInfiniteCountingUsage("test.value.count.100K+", 200 * 1000)
    assertInfiniteCountingUsage("test.value.count.500K+", 999999)
    assertInfiniteCountingUsage("test.value.count.1M+", 1000 * 1000)
    assertInfiniteCountingUsage("test.value.count.1M+", 2000 * 1000)
    assertInfiniteCountingUsage("test.value.count.5M+", 6000 * 1000)
    assertInfiniteCountingUsage("test.value.count.100M+", 2000 * 1000 * 100)
    assertInfiniteCountingUsage("test.value.count.MANY", 2000 * 1000 * 1000)
  }

  @Test
  fun `test counting usage on empty list`() {
    val emptySteps = listOf<Int>()
    assertCountingUsage("test.value.count.1", 1, emptySteps)
  }

  @Test
  fun `test counting usage if value is less than the first step`() {
    val steps = listOf(1, 5, 10)
    assertCountingUsage("test.value.count.<1", 0, steps)
  }

  @Test
  fun `test next power of two`() {
    testPowerOfTwo(0, 1)
    testPowerOfTwo(-5, 1)
    testPowerOfTwo(1, 1)
    testPowerOfTwo(2, 2)
    testPowerOfTwo(3, 4)
    testPowerOfTwo(5, 8)
  }

  private fun testPowerOfTwo(value: Int, expected: Int) {
    assertEquals(expected, getNextPowerOfTwo(value), "Incorrect key for value `$value`")
  }

  private fun assertCountingUsage(expectedKey: String, actualValue: Int, steps: List<Int>) {
    assertUsage(expectedKey, 1, getCountingUsage("test.value.count", actualValue, steps), "Incorrect key for value '$actualValue'")
  }

  private fun assertInfiniteCountingUsage(expectedKey: String, actualValue: Int) {
    assertUsage(expectedKey, 1, getCountingUsage("test.value.count", actualValue), "Incorrect key for value '$actualValue'")
  }

  private fun assertUsage(key: String, value: Int, actualUsage: UsageDescriptor, message: String? = null) {
    assertEquals(key, actualUsage.key, message)
    assertEquals(value, actualUsage.value, message)
  }
}

private fun getCountingUsage(key: String, value: Int, steps: List<Int>) : UsageDescriptor {
  return UsageDescriptor("$key." + getCountingStepName(value, steps), 1)
}