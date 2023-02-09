// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.testFramework.HeavyPlatformTestCase
import org.junit.Assert
import org.junit.Test

class UsageDescriptorUnitTest : HeavyPlatformTestCase() {

  @Test
  fun `test compare usage descriptor`() {
    Assert.assertEquals(MetricEvent("abcd"), MetricEvent("abcd"))
    Assert.assertNotEquals(MetricEvent("abcd"), MetricEvent("efgh"))
    Assert.assertNotEquals(MetricEvent("ijkl"), MetricEvent("efgh"))
  }
}