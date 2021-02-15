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
  fun `test create new metric`() {
    val newMetric = newMetric("metric.without.data")
    Assert.assertTrue(newMetric.eventId == "metric.without.data")
    Assert.assertTrue(newMetric.data.build().isEmpty())
  }

  @Test
  fun `test create new metric with data`() {
    val newMetric = newMetric("metric.with.data", FeatureUsageData().addPlace("MainMenu"))
    Assert.assertTrue(newMetric.eventId == "metric.with.data")
    Assert.assertTrue(newMetric.data.build().size == 1)
    Assert.assertTrue(newMetric.data.build()["place"] == "MainMenu")
  }

  @Test
  fun `test create new metric with value and data`() {
    val newMetric = newMetric("metric.with.data", "metric.value", FeatureUsageData().addPlace("MainMenu"))
    Assert.assertTrue(newMetric.eventId == "metric.with.data")
    Assert.assertTrue(newMetric.data.build().size == 2)
    Assert.assertTrue(newMetric.data.build()["value"] == "metric.value")
    Assert.assertTrue(newMetric.data.build()["place"] == "MainMenu")
  }

  @Test
  fun `test create new metric with string value`() {
    val newMetric = newMetric("metric.with.value", "metric.value")
    Assert.assertTrue(newMetric.eventId == "metric.with.value")
    Assert.assertTrue(newMetric.data.build().size == 1)
    Assert.assertTrue(newMetric.data.build()["value"] == "metric.value")
  }

  @Test
  fun `test create new metric with int value`() {
    val newMetric = newMetric("metric.with.value", 15)
    Assert.assertTrue(newMetric.eventId == "metric.with.value")
    Assert.assertTrue(newMetric.data.build().size == 1)
    Assert.assertTrue(newMetric.data.build()["value"] == 15)
  }

  @Test
  fun `test create new metric with float value`() {
    val newMetric = newMetric("metric.with.value", 1.3F)
    Assert.assertTrue(newMetric.eventId == "metric.with.value")
    Assert.assertTrue(newMetric.data.build().size == 1)
    Assert.assertTrue(newMetric.data.build()["value"] == 1.3F)
  }

  @Test
  fun `test create new metric with boolean value`() {
    val newMetric = newMetric("metric.with.value", true)
    Assert.assertTrue(newMetric.eventId == "metric.with.value")
    Assert.assertTrue(newMetric.data.build().size == 1)
    Assert.assertTrue(newMetric.data.build()["value"] == true)
  }

  @Test
  fun `test create new boolean metric`() {
    val newMetric = newBooleanMetric("metric.with.value", false)
    Assert.assertTrue(newMetric.eventId == "metric.with.value")
    Assert.assertTrue(newMetric.data.build().size == 1)
    Assert.assertTrue(newMetric.data.build()["enabled"] == false)
  }

  @Test
  fun `test create new boolean metric with data`() {
    val newMetric = newBooleanMetric("metric.with.value", false, FeatureUsageData().addData("language", "Java"))
    Assert.assertTrue(newMetric.eventId == "metric.with.value")
    Assert.assertTrue(newMetric.data.build().size == 2)
    Assert.assertTrue(newMetric.data.build()["enabled"] == false)
    Assert.assertTrue(newMetric.data.build()["language"] == "Java")
  }

  @Test
  fun `test create new counter metric`() {
    val newMetric = newCounterMetric("metric.with.value", 23)
    Assert.assertTrue(newMetric.eventId == "metric.with.value")
    Assert.assertTrue(newMetric.data.build().size == 1)
    Assert.assertTrue(newMetric.data.build()["count"] == 23)
  }

  @Test
  fun `test create new counter metric with data`() {
    val newMetric = newCounterMetric("metric.with.value", 23, FeatureUsageData().addData("language", "Java"))
    Assert.assertTrue(newMetric.eventId == "metric.with.value")
    Assert.assertTrue(newMetric.data.build().size == 2)
    Assert.assertTrue(newMetric.data.build()["count"] == 23)
    Assert.assertTrue(newMetric.data.build()["language"] == "Java")
  }

  @Test
  fun `test add all default values metric`() {
    val result = HashSet<MetricEvent>()
    val obj = MetricEventTestObj()
    val default = MetricEventTestObj()

    addIfDiffers(result, obj, default, { o -> o.strValue }, "metric.string")
    addIfDiffers(result, obj, default, { o -> o.intValue }, "metric.int")
    addIfDiffers(result, obj, default, { o -> o.floatValue }, "metric.float")
    Assert.assertTrue(result.isEmpty())
  }

  @Test
  fun `test add string not default metric`() {
    val result = HashSet<MetricEvent>()
    val obj = MetricEventTestObj()
    obj.strValue = "new.value"

    val default = MetricEventTestObj()

    addIfDiffers(result, obj, default, { o -> o.strValue }, "metric.string")
    addIfDiffers(result, obj, default, { o -> o.intValue }, "metric.int")
    addIfDiffers(result, obj, default, { o -> o.floatValue }, "metric.float")
    Assert.assertTrue(result.size == 1)
    for (event in result) {
      Assert.assertTrue(event.eventId == "metric.string")
      Assert.assertTrue(event.data.build()["value"] == "new.value")
    }
  }

  @Test
  fun `test add string not default metric with data`() {
    val result = HashSet<MetricEvent>()
    val obj = MetricEventTestObj()
    obj.strValue = "new.value"

    val default = MetricEventTestObj()
    val data = FeatureUsageData().addPlace("MainMenu")

    addIfDiffers(result, obj, default, { o -> o.strValue }, "metric.string", data)
    addIfDiffers(result, obj, default, { o -> o.intValue }, "metric.int", data)
    addIfDiffers(result, obj, default, { o -> o.floatValue }, "metric.float", data)
    Assert.assertTrue(result.size == 1)
    for (event in result) {
      Assert.assertTrue(event.eventId == "metric.string")
      Assert.assertTrue(event.data.build()["value"] == "new.value")
      Assert.assertTrue(event.data.build()["place"] == "MainMenu")
    }
  }

  @Test
  fun `test add int not default metric`() {
    val result = HashSet<MetricEvent>()
    val obj = MetricEventTestObj()
    obj.intValue = 15

    val default = MetricEventTestObj()

    addIfDiffers(result, obj, default, { o -> o.strValue }, "metric.string")
    addIfDiffers(result, obj, default, { o -> o.intValue }, "metric.int")
    addIfDiffers(result, obj, default, { o -> o.floatValue }, "metric.float")
    Assert.assertTrue(result.size == 1)
    for (event in result) {
      Assert.assertTrue(event.eventId == "metric.int")
      Assert.assertTrue(event.data.build()["value"] == 15)
    }
  }

  @Test
  fun `test add float not default metric`() {
    val result = HashSet<MetricEvent>()
    val obj = MetricEventTestObj()
    obj.floatValue = 0.8F

    val default = MetricEventTestObj()

    addIfDiffers(result, obj, default, { o -> o.strValue }, "metric.string")
    addIfDiffers(result, obj, default, { o -> o.intValue }, "metric.int")
    addIfDiffers(result, obj, default, { o -> o.floatValue }, "metric.float")
    addIfDiffers(result, obj, default, { o -> o.boolValue }, "metric.bool")
    Assert.assertTrue(result.size == 1)
    for (event in result) {
      Assert.assertTrue(event.eventId == "metric.float")
      Assert.assertTrue(event.data.build()["value"] == 0.8F)
    }
  }

  @Test
  fun `test add all not default metrics`() {
    val result = HashSet<MetricEvent>()
    val obj = MetricEventTestObj()
    obj.strValue = "new.value"
    obj.intValue = 15
    obj.floatValue = 0.8F

    val default = MetricEventTestObj()

    addIfDiffers(result, obj, default, { o -> o.strValue }, "metric.string")
    addIfDiffers(result, obj, default, { o -> o.intValue }, "metric.int")
    addIfDiffers(result, obj, default, { o -> o.floatValue }, "metric.float")
    Assert.assertTrue(result.size == 3)
    for (event in result) {
      Assert.assertTrue(event.eventId in listOf("metric.string", "metric.int", "metric.float"))
      when {
        event.eventId == "metric.string" -> Assert.assertTrue(event.data.build()["value"] == "new.value")
        event.eventId == "metric.int" -> Assert.assertTrue(event.data.build()["value"] == 15)
        event.eventId == "metric.float" -> Assert.assertTrue(event.data.build()["value"] == 0.8F)
      }
    }
  }

  @Test
  fun `test add all not default metrics with data`() {
    val result = HashSet<MetricEvent>()
    val obj = MetricEventTestObj()
    obj.strValue = "new.value"
    obj.intValue = 15
    obj.floatValue = 0.8F

    val default = MetricEventTestObj()
    val data = FeatureUsageData().addPlace("MainMenu")

    addIfDiffers(result, obj, default, { o -> o.strValue }, "metric.string", data)
    addIfDiffers(result, obj, default, { o -> o.intValue }, "metric.int", data)
    addIfDiffers(result, obj, default, { o -> o.floatValue }, "metric.float", data)
    Assert.assertTrue(result.size == 3)
    for (event in result) {
      Assert.assertTrue(event.eventId in listOf("metric.string", "metric.int", "metric.float"))
      Assert.assertTrue(event.data.build()["place"] == "MainMenu")
      when {
        event.eventId == "metric.string" -> Assert.assertTrue(event.data.build()["value"] == "new.value")
        event.eventId == "metric.int" -> Assert.assertTrue(event.data.build()["value"] == 15)
        event.eventId == "metric.float" -> Assert.assertTrue(event.data.build()["value"] == 0.8F)
      }
    }
  }

  @Test
  fun `test add enabled default metric`() {
    val result = HashSet<MetricEvent>()
    val obj = MetricEventTestObj()
    val default = MetricEventTestObj()

    addBoolIfDiffers(result, obj, default, { o -> o.boolValue }, "metric.bool")
    Assert.assertTrue(result.isEmpty())
  }

  @Test
  fun `test add enabled not default metric`() {
    val result = HashSet<MetricEvent>()
    val obj = MetricEventTestObj()
    obj.boolValue = true

    val default = MetricEventTestObj()

    addBoolIfDiffers(result, obj, default, { o -> o.boolValue }, "metric.bool")
    Assert.assertTrue(result.size == 1)
    for (event in result) {
      Assert.assertTrue(event.eventId == "metric.bool")
      Assert.assertTrue(event.data.build()["enabled"] == true)
    }
  }

  @Test
  fun `test add enabled not default metric with data`() {
    val result = HashSet<MetricEvent>()
    val obj = MetricEventTestObj()
    obj.boolValue = true

    val default = MetricEventTestObj()
    val data = FeatureUsageData().addPlace("MainMenu")

    addBoolIfDiffers(result, obj, default, { o -> o.boolValue }, "metric.bool", data)
    Assert.assertTrue(result.size == 1)
    for (event in result) {
      Assert.assertTrue(event.eventId == "metric.bool")
      Assert.assertTrue(event.data.build()["enabled"] == true)
      Assert.assertTrue(event.data.build()["place"] == "MainMenu")
    }
  }

  @Test
  fun `test add count default metric`() {
    val result = HashSet<MetricEvent>()
    val obj = MetricEventTestObj()
    val default = MetricEventTestObj()

    addCounterIfDiffers(result, obj, default, { o -> o.intValue }, "metric.count")
    Assert.assertTrue(result.isEmpty())
  }

  @Test
  fun `test add count not default metric`() {
    val result = HashSet<MetricEvent>()
    val obj = MetricEventTestObj()
    obj.intValue = 23

    val default = MetricEventTestObj()

    addCounterIfDiffers(result, obj, default, { o -> o.intValue }, "metric.count")
    Assert.assertTrue(result.size == 1)
    for (event in result) {
      Assert.assertTrue(event.eventId == "metric.count")
      Assert.assertTrue(event.data.build()["count"] == 23)
    }
  }

  @Test
  fun `test add count not default metric with data`() {
    val result = HashSet<MetricEvent>()
    val obj = MetricEventTestObj()
    obj.intValue = 23

    val default = MetricEventTestObj()
    val data = FeatureUsageData().addPlace("MainMenu")

    addCounterIfDiffers(result, obj, default, { o -> o.intValue }, "metric.count", data)
    Assert.assertTrue(result.size == 1)
    for (event in result) {
      Assert.assertTrue(event.eventId == "metric.count")
      Assert.assertTrue(event.data.build()["count"] == 23)
      Assert.assertTrue(event.data.build()["place"] == "MainMenu")
    }
  }

  class MetricEventTestObj(
    var strValue: String = "default.str",
    var intValue: Int = 10,
    var floatValue: Float = 1.4F,
    var boolValue: Boolean = false
  )
}