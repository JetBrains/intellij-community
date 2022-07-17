// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newCounterMetric
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.containers.ContainerUtil.newArrayList
import org.junit.Assert
import org.junit.Test

class UsageDescriptorUnitTest : HeavyPlatformTestCase() {

  @Test
  fun `test compare usage descriptor`() {
    Assert.assertEquals(MetricEvent("abcd"), MetricEvent("abcd"))
    Assert.assertNotEquals(MetricEvent("abcd"), MetricEvent("efgh"))
    Assert.assertNotEquals(MetricEvent("ijkl"), MetricEvent("efgh"))
  }

  @Test
  fun `test compare usage descriptor with value`() {
    Assert.assertEquals(newCounterMetric("abcd", 1), newCounterMetric("abcd", 1))
    Assert.assertNotEquals(newCounterMetric("abcd", 1), newCounterMetric("abcd", 5))
    Assert.assertNotEquals(newCounterMetric("abcd", 5), newCounterMetric("abcd", 2))
    Assert.assertNotEquals(newCounterMetric("abcd", 1), newCounterMetric("efgh", 1))
    Assert.assertNotEquals(newCounterMetric("abcd", 1), newCounterMetric("efgh", 2))
    Assert.assertNotEquals(newCounterMetric("abcd", 7), newCounterMetric("efgh", 2))
    Assert.assertNotEquals(newCounterMetric("ijkl", 1), newCounterMetric("efgh", 1))
    Assert.assertNotEquals(newCounterMetric("ijkl", 2), newCounterMetric("efgh", 3))
    Assert.assertNotEquals(newCounterMetric("ijkl", 5), newCounterMetric("efgh", 3))
  }

  private fun doTestUniqueFilter(expectedSize: Int, all: List<MetricEvent>) {
    val unique = HashSet(all)
    Assert.assertTrue(unique.size == expectedSize)
    for (descriptor in all) {
      Assert.assertTrue(unique.contains(descriptor))
    }
  }

  @Test
  fun `test filter unique usage descriptors`() {
    doTestUniqueFilter(2, newArrayList(
      newMetric("abcd"), newMetric("abcd"), newMetric("efgh")
    ))
  }

  @Test
  fun `test filter unique usage descriptors with value`() {
    doTestUniqueFilter(3, newArrayList(
      newCounterMetric("abcd", 1), newCounterMetric("abcd", 1), newCounterMetric("efgh", 1), newCounterMetric("abcd", 2)
    ))
  }

  @Test
  fun `test filter unique usage descriptors with the same value`() {
    doTestUniqueFilter(1, newArrayList(
      newCounterMetric("abcd", 1), newCounterMetric("abcd", 1), newCounterMetric("abcd", 1)
    ))
  }

  @Test
  fun `test filter unique usage descriptors with data`() {
    doTestUniqueFilter(2, newArrayList(
      newCounterMetric("abcd", 1, FeatureUsageData().addData("foo", 123)),
      newCounterMetric("abcd", 1)
    ))
  }

  @Test
  fun `test filter unique usage descriptors with different data`() {
    doTestUniqueFilter(2, newArrayList(
      newCounterMetric("abcd", 1, FeatureUsageData().addData("foo", 123)),
      newCounterMetric("abcd", 1, FeatureUsageData().addData("foo", 122))
    ))
  }

  @Test
  fun `test filter unique usage descriptors with the same data`() {
    doTestUniqueFilter(1, newArrayList(
      newCounterMetric("abcd", 1, FeatureUsageData().addData("foo", 123)),
      newCounterMetric("abcd", 1, FeatureUsageData().addData("foo", 123))
    ))
  }

  @Test
  fun `test filter unique usage descriptors with the same value and different data`() {
    doTestUniqueFilter(2, newArrayList(
      newCounterMetric("abcd", 1, FeatureUsageData().addData("foo", 123)),
      newCounterMetric("abcd", 1, FeatureUsageData().addData("foo", 12))
    ))
  }

  @Test
  fun `test filter unique usage descriptors with different value and same data`() {
    doTestUniqueFilter(2, newArrayList(
      newCounterMetric("abcd", 1, FeatureUsageData().addData("foo", 123)),
      newCounterMetric("abcd", 2, FeatureUsageData().addData("foo", 123))
    ))
  }
}