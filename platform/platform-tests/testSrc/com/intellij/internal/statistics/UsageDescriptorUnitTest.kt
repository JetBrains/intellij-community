// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.testFramework.PlatformTestCase
import com.intellij.util.containers.ContainerUtil.newArrayList
import gnu.trove.THashSet
import org.junit.Assert
import org.junit.Test

class UsageDescriptorUnitTest : PlatformTestCase() {

  @Test
  fun `test compare usage descriptor`() {
    Assert.assertEquals(UsageDescriptor("abcd"), UsageDescriptor("abcd"))
    Assert.assertNotEquals(UsageDescriptor("abcd"), UsageDescriptor("efgh"))
    Assert.assertNotEquals(UsageDescriptor("ijkl"), UsageDescriptor("efgh"))
  }

  @Test
  fun `test compare usage descriptor with value`() {
    Assert.assertEquals(UsageDescriptor("abcd", 1), UsageDescriptor("abcd", 1))
    Assert.assertNotEquals(UsageDescriptor("abcd", 1), UsageDescriptor("abcd", 5))
    Assert.assertNotEquals(UsageDescriptor("abcd", 5), UsageDescriptor("abcd", 2))
    Assert.assertNotEquals(UsageDescriptor("abcd", 1), UsageDescriptor("efgh", 1))
    Assert.assertNotEquals(UsageDescriptor("abcd", 1), UsageDescriptor("efgh", 2))
    Assert.assertNotEquals(UsageDescriptor("abcd", 7), UsageDescriptor("efgh", 2))
    Assert.assertNotEquals(UsageDescriptor("ijkl", 1), UsageDescriptor("efgh", 1))
    Assert.assertNotEquals(UsageDescriptor("ijkl", 2), UsageDescriptor("efgh", 3))
    Assert.assertNotEquals(UsageDescriptor("ijkl", 5), UsageDescriptor("efgh", 3))
  }

  private fun doTestUniqueFilter(expectedSize: Int, all: List<UsageDescriptor>) {
    val unique = THashSet<UsageDescriptor>(all)
    Assert.assertTrue(unique.size == expectedSize)
    for (descriptor in all) {
      Assert.assertTrue(unique.contains(descriptor))
    }
  }

  @Test
  fun `test filter unique usage descriptors`() {
    doTestUniqueFilter(2, newArrayList(
      UsageDescriptor("abcd"), UsageDescriptor("abcd"), UsageDescriptor("efgh")
    ))
  }

  @Test
  fun `test filter unique usage descriptors with value`() {
    doTestUniqueFilter(3, newArrayList<UsageDescriptor>(
      UsageDescriptor("abcd", 1), UsageDescriptor("abcd", 1), UsageDescriptor("efgh", 1), UsageDescriptor("abcd", 2)
    ))
  }

  @Test
  fun `test filter unique usage descriptors with the same value`() {
    doTestUniqueFilter(1, newArrayList<UsageDescriptor>(
      UsageDescriptor("abcd", 1), UsageDescriptor("abcd", 1), UsageDescriptor("abcd", 1)
    ))
  }

  @Test
  fun `test filter unique usage descriptors with data`() {
    doTestUniqueFilter(2, newArrayList<UsageDescriptor>(
      UsageDescriptor("abcd", 1, FeatureUsageData().addData("foo", 123)),
      UsageDescriptor("abcd", 1)
    ))
  }

  @Test
  fun `test filter unique usage descriptors with different data`() {
    doTestUniqueFilter(2, newArrayList<UsageDescriptor>(
      UsageDescriptor("abcd", 1, FeatureUsageData().addData("foo", 123)),
      UsageDescriptor("abcd", 1, FeatureUsageData().addData("foo", 122))
    ))
  }

  @Test
  fun `test filter unique usage descriptors with the same data`() {
    doTestUniqueFilter(1, newArrayList<UsageDescriptor>(
      UsageDescriptor("abcd", 1, FeatureUsageData().addData("foo", 123)),
      UsageDescriptor("abcd", 1, FeatureUsageData().addData("foo", 123))
    ))
  }

  @Test
  fun `test filter unique usage descriptors with the same value and different data`() {
    doTestUniqueFilter(2, newArrayList<UsageDescriptor>(
      UsageDescriptor("abcd", 1, FeatureUsageData().addData("foo", 123)),
      UsageDescriptor("abcd", 1, FeatureUsageData().addData("foo", 12))
    ))
  }

  @Test
  fun `test filter unique usage descriptors with different value and same data`() {
    doTestUniqueFilter(2, newArrayList<UsageDescriptor>(
      UsageDescriptor("abcd", 1, FeatureUsageData().addData("foo", 123)),
      UsageDescriptor("abcd", 2, FeatureUsageData().addData("foo", 123))
    ))
  }
}