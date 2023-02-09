// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("DEPRECATION")

package com.intellij.internal.statistics

import com.intellij.internal.statistic.beans.*
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class MetricEventTest : BasePlatformTestCase() {

  @Test
  fun `test create new metric with data`() {
    val newMetric = newMetric("metric.with.data", FeatureUsageData().addPlace("MainMenu"))
    Assert.assertTrue(newMetric.eventId == "metric.with.data")
    Assert.assertTrue(newMetric.data.build().size == 1)
    Assert.assertTrue(newMetric.data.build()["place"] == "MainMenu")
  }

  @Test
  fun `test create new metric with string value`() {
    val newMetric = newMetric("metric.with.value", "metric.value")
    Assert.assertTrue(newMetric.eventId == "metric.with.value")
    Assert.assertTrue(newMetric.data.build().size == 1)
    Assert.assertTrue(newMetric.data.build()["value"] == "metric.value")
  }
}