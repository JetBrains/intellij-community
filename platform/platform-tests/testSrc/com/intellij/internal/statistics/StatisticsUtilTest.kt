/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal.statistics

import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.utils.getBooleanUsage
import com.intellij.internal.statistic.utils.getCountingUsage
import org.junit.Test
import kotlin.test.assertEquals

class StatisticsUtilTest {

  @Test
  fun test_boolean_usage() {
    assertUsage("test.value.enabled", 1, getBooleanUsage("test.value", true))
    assertUsage("test.value.disabled", 1, getBooleanUsage("test.value", false))
  }

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
  fun `test counting usage on empty list`() {
    val emptySteps = listOf<Int>()
    assertCountingUsage("test.value.count.1", 1, emptySteps)
  }

  @Test
  fun `test counting usage if value is less than the first step`() {
    val steps = listOf(1, 5, 10)
    assertCountingUsage("test.value.count.<1", 0, steps)
  }

  private fun assertCountingUsage(expectedKey: String, actualValue: Int, steps: List<Int>) {
    assertUsage(expectedKey, 1, getCountingUsage("test.value.count", actualValue, steps), "Incorrect key for value '$actualValue'")
  }

  private fun assertUsage(key: String, value: Int, actualUsage: UsageDescriptor, message: String? = null) {
    assertEquals(key, actualUsage.key, message)
    assertEquals(value, actualUsage.value, message)
  }
}