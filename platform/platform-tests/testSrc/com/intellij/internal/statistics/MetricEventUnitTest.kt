// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("DEPRECATION")

package com.intellij.internal.statistics

import com.intellij.internal.statistic.beans.*
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.testFramework.PlatformTestCase
import com.intellij.util.containers.ContainerUtil.newArrayList
import gnu.trove.THashSet
import org.junit.Assert
import org.junit.Test

class MetricEventUnitTest : PlatformTestCase() {

  @Test
  fun `test compare metric events`() {
    Assert.assertEquals(newMetric("abcd"), newMetric("abcd"))
    Assert.assertNotEquals(newMetric("abcd"), newMetric("efgh"))
    Assert.assertNotEquals(newMetric("ijkl"), newMetric("efgh"))
  }

  @Test
  fun `test compare usage metric events with int value`() {
    Assert.assertEquals(newMetric("abcd", 1), newMetric("abcd", 1))
    Assert.assertEquals(newMetric("abcd", 5), newMetric("abcd", 5))
    Assert.assertNotEquals(newMetric("abcd", 1), newCounterMetric("abcd", 1))
    Assert.assertNotEquals(newMetric("abcd"), newCounterMetric("abcd", 1))
    Assert.assertNotEquals(newMetric("abcd"), newCounterMetric("abcd", 1))

    Assert.assertNotEquals(newMetric("abcd", 1), newMetric("abcd"))
    Assert.assertNotEquals(newMetric("abcd", 1), newMetric("abcd", 5))
    Assert.assertNotEquals(newMetric("abcd", 5), newMetric("abcd", 2))
    Assert.assertNotEquals(newMetric("abcd", 1), newMetric("efgh", 1))
    Assert.assertNotEquals(newMetric("abcd", 1), newMetric("efgh", 2))
    Assert.assertNotEquals(newMetric("abcd", 7), newMetric("efgh", 2))
    Assert.assertNotEquals(newMetric("ijkl", 1), newMetric("efgh", 1))
    Assert.assertNotEquals(newMetric("ijkl", 2), newMetric("efgh", 3))
    Assert.assertNotEquals(newMetric("ijkl", 5), newMetric("efgh", 3))
  }

  @Test
  fun `test compare usage metric events with counter`() {
    Assert.assertEquals(newCounterMetric("abcd", 1), newCounterMetric("abcd", 1))
    Assert.assertEquals(newCounterMetric("abcd", 5), newCounterMetric("abcd", 5))

    Assert.assertNotEquals(newCounterMetric("abcd", 1), newCounterMetric("abcd", 5))
    Assert.assertNotEquals(newCounterMetric("abcd", 5), newCounterMetric("abcd", 2))
    Assert.assertNotEquals(newCounterMetric("abcd", 1), newCounterMetric("efgh", 1))
    Assert.assertNotEquals(newCounterMetric("abcd", 1), newCounterMetric("efgh", 2))
    Assert.assertNotEquals(newCounterMetric("abcd", 7), newCounterMetric("efgh", 2))
    Assert.assertNotEquals(newCounterMetric("ijkl", 1), newCounterMetric("efgh", 1))
    Assert.assertNotEquals(newCounterMetric("ijkl", 2), newCounterMetric("efgh", 3))
    Assert.assertNotEquals(newCounterMetric("ijkl", 5), newCounterMetric("efgh", 3))
  }

  @Test
  fun `test compare usage metric events with counter range`() {
    Assert.assertEquals(newCounterRangeMetric("abcd", 1), newCounterRangeMetric("abcd", 1))
    Assert.assertEquals(newCounterRangeMetric("abcd", 5), newCounterRangeMetric("abcd", 5))
    Assert.assertNotEquals(newCounterRangeMetric("abcd", 1), newCounterRangeMetric("abcd", 5))
    Assert.assertNotEquals(newCounterRangeMetric("abcd", 5), newCounterRangeMetric("abcd", 2))
    Assert.assertNotEquals(newCounterRangeMetric("abcd", 1), newCounterRangeMetric("efgh", 1))
    Assert.assertNotEquals(newCounterRangeMetric("abcd", 1), newCounterRangeMetric("efgh", 2))
    Assert.assertNotEquals(newCounterRangeMetric("abcd", 7), newCounterRangeMetric("efgh", 2))
    Assert.assertNotEquals(newCounterRangeMetric("ijkl", 1), newCounterRangeMetric("efgh", 1))
    Assert.assertNotEquals(newCounterRangeMetric("ijkl", 2), newCounterRangeMetric("efgh", 3))
    Assert.assertNotEquals(newCounterRangeMetric("ijkl", 5), newCounterRangeMetric("efgh", 3))
  }

  @Test
  fun `test compare usage metric events with boolean`() {
    Assert.assertEquals(newMetric("abcd", true), newMetric("abcd", true))
    Assert.assertEquals(newMetric("abcd", false), newMetric("abcd", false))
    Assert.assertEquals(newBooleanMetric("abcd", true), newBooleanMetric("abcd", true))
    Assert.assertEquals(newBooleanMetric("abcd", false), newBooleanMetric("abcd", false))

    Assert.assertNotEquals(newMetric("abcd", true), newBooleanMetric("abcd", true))
    Assert.assertNotEquals(newMetric("abcd"), newBooleanMetric("abcd", true))

    Assert.assertNotEquals(newMetric("abcd", true), newMetric("abcd", false))
    Assert.assertNotEquals(newMetric("abcd", true), newMetric("efgh", true))
    Assert.assertNotEquals(newMetric("abcd", true), newMetric("efgh", false))

    Assert.assertNotEquals(newBooleanMetric("abcd", true), newBooleanMetric("abcd", false))
    Assert.assertNotEquals(newBooleanMetric("abcd", true), newBooleanMetric("efgh", true))
    Assert.assertNotEquals(newBooleanMetric("abcd", true), newBooleanMetric("efgh", false))
  }

  private fun doTestUniqueFilter(expectedSize: Int, all: List<MetricEvent>) {
    val unique = THashSet<MetricEvent>(all)
    Assert.assertTrue(unique.size == expectedSize)
    for (descriptor in all) {
      Assert.assertTrue(unique.contains(descriptor))
    }
  }

  @Test
  fun `test filter unique metric events`() {
    doTestUniqueFilter(2, newArrayList(
      newMetric("abcd"), newMetric("abcd"), newMetric("efgh")
    ))
  }

  @Test
  fun `test filter unique metric events with value`() {
    doTestUniqueFilter(3, newArrayList(
      newMetric("abcd"), newMetric("abcd"), newMetric("efgh"), newMetric("abcd", 2)
    ))
  }

  @Test
  fun `test filter unique metric events all with value`() {
    doTestUniqueFilter(3, newArrayList(
      newMetric("abcd", 1), newMetric("abcd", 1), newMetric("efgh", 1), newMetric("abcd", 2)
    ))
  }

  @Test
  fun `test filter unique metric events all with the same value`() {
    doTestUniqueFilter(1, newArrayList(
      newMetric("abcd", 1), newMetric("abcd", 1), newMetric("abcd", 1)
    ))
  }

  @Test
  fun `test filter unique metric events with data`() {
    doTestUniqueFilter(2, newArrayList(
      newMetric("abcd", 1, FeatureUsageData().addData("foo", 123)),
      newMetric("abcd", 1)
    ))
  }

  @Test
  fun `test filter unique metric events with different data`() {
    doTestUniqueFilter(2, newArrayList(
      newMetric("abcd", 1, FeatureUsageData().addData("foo", 123)),
      newMetric("abcd", 1, FeatureUsageData().addData("foo", 122))
    ))
  }

  @Test
  fun `test filter unique metric events with the same data`() {
    doTestUniqueFilter(1, newArrayList(
      newMetric("abcd", 1, FeatureUsageData().addData("foo", 123)),
      newMetric("abcd", 1, FeatureUsageData().addData("foo", 123))
    ))
  }

  @Test
  fun `test filter unique metric events with the same value and different data`() {
    doTestUniqueFilter(2, newArrayList(
      newMetric("abcd", 1, FeatureUsageData().addData("foo", 123)),
      newMetric("abcd", 1, FeatureUsageData().addData("foo", 12))
    ))
  }

  @Test
  fun `test filter unique metric events with different value and same data`() {
    doTestUniqueFilter(2, newArrayList(
      newMetric("abcd", 1, FeatureUsageData().addData("foo", 123)),
      newMetric("abcd", 2, FeatureUsageData().addData("foo", 123))
    ))
  }
}