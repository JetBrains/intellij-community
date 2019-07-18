// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.util.Version
import com.intellij.testFramework.PlatformTestCase
import org.junit.Assert
import org.junit.Test

class FeatureUsageDataTest : PlatformTestCase() {

  @Test
  fun `test empty data`() {
    val build = FeatureUsageData().build()
    Assert.assertTrue(build.isEmpty())
  }

  @Test
  fun `test put string data`() {
    val build = FeatureUsageData().addData("key", "my-value").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build["key"] == "my-value")
  }

  @Test
  fun `test put int data`() {
    val build = FeatureUsageData().addData("key", 99).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build["key"] == 99)
  }

  @Test
  fun `test put long data`() {
    val value: Long = 99
    val build = FeatureUsageData().addData("key", value).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build["key"] == 99L)
  }

  @Test
  fun `test put boolean data`() {
    val build = FeatureUsageData().addData("key", true).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build["key"] == true)
  }

  @Test
  fun `test put os data`() {
    val build = FeatureUsageData().addOS().build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("os"))
  }

  @Test
  fun `test put add null place`() {
    val build = FeatureUsageData().addPlace(null).build()
    Assert.assertTrue(build.isEmpty())
  }

  @Test
  fun `test put add empty place`() {
    val build = FeatureUsageData().addPlace("").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("place"))
    Assert.assertTrue(build["place"] == ActionPlaces.UNKNOWN)
  }

  @Test
  fun `test put add common place`() {
    val build = FeatureUsageData().addPlace("TestTreeViewToolbar").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("place"))
    Assert.assertTrue(build["place"] == "TestTreeViewToolbar")
  }

  @Test
  fun `test put add common popup place`() {
    val build = FeatureUsageData().addPlace("FavoritesPopup").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("place"))
    Assert.assertTrue(build["place"] == "FavoritesPopup")
  }

  @Test
  fun `test put add custom popup place`() {
    val build = FeatureUsageData().addPlace("popup@my-custom-popup").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("place"))
    Assert.assertTrue(build["place"] == ActionPlaces.POPUP)
  }

  @Test
  fun `test put add custom place`() {
    val build = FeatureUsageData().addPlace("my-custom-popup").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("place"))
    Assert.assertTrue(build["place"] == ActionPlaces.UNKNOWN)
  }

  @Test
  fun `test put null obj version`() {
    val build = FeatureUsageData().addVersion(null).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "unknown.format")
  }

  @Test
  fun `test put null version`() {
    val build = FeatureUsageData().addVersionByString(null).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "unknown")
  }

  @Test
  fun `test put obj version`() {
    val build = FeatureUsageData().addVersion(Version(3, 5, 0)).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "3.5")
  }

  @Test
  fun `test put version`() {
    val build = FeatureUsageData().addVersionByString("5.11.0").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "5.11")
  }

  @Test
  fun `test put obj version with bugfix`() {
    val build = FeatureUsageData().addVersion(Version(3, 5, 9)).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "3.5")
  }

  @Test
  fun `test put version with bugfix`() {
    val build = FeatureUsageData().addVersionByString("5.11.98").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "5.11")
  }

  @Test
  fun `test put invalid version`() {
    val build = FeatureUsageData().addVersionByString("2018-11").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "2018.0")
  }

  @Test
  fun `test put empty version`() {
    val build = FeatureUsageData().addVersionByString("").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "unknown.format")
  }

  @Test
  fun `test put version with snapshot`() {
    val build = FeatureUsageData().addVersionByString("1.4-SNAPSHOT").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "1.4")
  }

  @Test
  fun `test put version with suffix`() {
    val build = FeatureUsageData().addVersionByString("2.5.0.BUILD-2017091412345").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "2.5")
  }

  @Test
  fun `test put version with letters`() {
    val build = FeatureUsageData().addVersionByString("abcd").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "unknown.format")
  }

  @Test
  fun `test copy empty data`() {
    val data = FeatureUsageData()
    val data2 = data.copy()
    Assert.assertTrue(data.build().isEmpty())
    Assert.assertEquals(data, data2)
    Assert.assertEquals(data.build(), data2.build())
  }

  @Test
  fun `test copy single element data`() {
    val data = FeatureUsageData().addData("new-key", "new-value")
    val data2 = data.copy()
    Assert.assertTrue(data.build().size == 1)
    Assert.assertTrue(data.build()["new-key"] == "new-value")
    Assert.assertEquals(data, data2)
    Assert.assertEquals(data.build(), data2.build())
  }

  @Test
  fun `test copy multi elements data`() {
    val data = FeatureUsageData().addData("new-key", "new-value").addData("second", "test")
    val data2 = data.copy()
    Assert.assertTrue(data.build().size == 2)
    Assert.assertTrue(data.build()["new-key"] == "new-value")
    Assert.assertTrue(data.build()["second"] == "test")
    Assert.assertEquals(data, data2)
    Assert.assertEquals(data.build(), data2.build())
  }

  @Test
  fun `test merge empty data`() {
    val data = FeatureUsageData()
    val data2 = FeatureUsageData()
    data.merge(data2, "pr")

    Assert.assertTrue(data.build().isEmpty())
  }

  @Test
  fun `test merge empty with not empty data`() {
    val data = FeatureUsageData()
    val data2 = FeatureUsageData().addData("key", "value")
    data.merge(data2, "pr")

    val build = data.build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build["key"] == "value")
  }

  @Test
  fun `test merge empty with default not empty data`() {
    val data = FeatureUsageData()
    val data2 = FeatureUsageData().addData("data_1", "value")
    data.merge(data2, "pr_")

    val build = data.build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build["pr_data_1"] == "value")
  }

  @Test
  fun `test merge not empty with empty data`() {
    val data = FeatureUsageData().addData("key", "value")
    val data2 = FeatureUsageData()
    data.merge(data2, "pr")

    val build = data.build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build["key"] == "value")
  }

  @Test
  fun `test merge not empty default with empty data`() {
    val data = FeatureUsageData().addData("data_2", "value")
    val data2 = FeatureUsageData()
    data.merge(data2, "pr")

    val build = data.build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build["data_2"] == "value")
  }

  @Test
  fun `test merge not empty data`() {
    val data = FeatureUsageData().addData("first", "value-1")
    val data2 = FeatureUsageData().addData("second", "value-2").addData("data_99", "default-value")
    data.merge(data2, "pr")

    Assert.assertTrue(data.build().size == 3)
    Assert.assertTrue(data.build()["first"] == "value-1")
    Assert.assertTrue(data.build()["second"] == "value-2")
    Assert.assertTrue(data.build()["prdata_99"] == "default-value")
  }

  @Test
  fun `test merge null group with null event data`() {
    val merged = FUStateUsagesLogger.mergeWithEventData(null, null)
    Assert.assertNull(merged)
  }

  @Test
  fun `test merge null group with count event data`() {
    val data = FeatureUsageData().addCount(10)
    val merged = FUStateUsagesLogger.mergeWithEventData(null, data)
    Assert.assertNotNull(merged)

    val build = merged!!.build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build["count"] == 10)
  }

  @Test
  fun `test merge group with null event data`() {
    val group = FeatureUsageData().addData("first", "value-1")
    val merged = FUStateUsagesLogger.mergeWithEventData(group, null)
    Assert.assertNotNull(merged)

    val build = merged!!.build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build["first"] == "value-1")
  }

  @Test
  fun `test merge count group with null event data`() {
    val group = FeatureUsageData().addData("first", "value-1").addCount(10)
    val merged = FUStateUsagesLogger.mergeWithEventData(group, null)
    Assert.assertNotNull(merged)

    val build = merged!!.build()
    Assert.assertTrue(build.size == 2)
    Assert.assertTrue(build["first"] == "value-1")
    Assert.assertTrue(build["count"] == 10)
  }

  @Test
  fun `test merge null group with event data`() {
    val event = FeatureUsageData().addData("first", 99)
    val merged = FUStateUsagesLogger.mergeWithEventData(null, event)
    Assert.assertNotNull(merged)

    val build = merged!!.build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build["first"] == 99)
  }

  @Test
  fun `test merge null group with default named event data`() {
    val event = FeatureUsageData().addData("data_5", 99)
    val merged = FUStateUsagesLogger.mergeWithEventData(null, event)
    Assert.assertNotNull(merged)

    val build = merged!!.build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build["event_data_5"] == 99)
  }

  @Test
  fun `test merge null group with event data with count`() {
    val event = FeatureUsageData().addData("first", true).addCount(10)
    val merged = FUStateUsagesLogger.mergeWithEventData(null, event)
    Assert.assertNotNull(merged)

    val build = merged!!.build()
    Assert.assertTrue(build.size == 2)
    Assert.assertTrue(build["first"] == true)
    Assert.assertTrue(build["count"] == 10)
  }

  @Test
  fun `test merge null group with default named event data with count`() {
    val event = FeatureUsageData().addData("data_9", true).addCount(7)
    val merged = FUStateUsagesLogger.mergeWithEventData(null, event)
    Assert.assertNotNull(merged)

    val build = merged!!.build()
    Assert.assertTrue(build.size == 2)
    Assert.assertTrue(build["event_data_9"] == true)
    Assert.assertTrue(build["count"] == 7)
  }

  @Test
  fun `test merge group with event data`() {
    val group = FeatureUsageData().addData("first", "value-1")
    val event = FeatureUsageData().addData("second", "value-2").addData("data_99", "default-value")
    val merged = FUStateUsagesLogger.mergeWithEventData(group, event)

    val build = merged!!.build()
    Assert.assertTrue(build.size == 3)
    Assert.assertTrue(build["first"] == "value-1")
    Assert.assertTrue(build["second"] == "value-2")
    Assert.assertTrue(build["event_data_99"] == "default-value")
  }

  @Test
  fun `test merge group with event data with not default value`() {
    val group = FeatureUsageData().addData("first", "value-1").addCount(10)
    val event = FeatureUsageData().addData("second", "value-2").addData("data_99", "default-value")
    val merged = FUStateUsagesLogger.mergeWithEventData(group, event)

    val build = merged!!.build()
    Assert.assertTrue(build.size == 4)
    Assert.assertTrue(build["first"] == "value-1")
    Assert.assertTrue(build["second"] == "value-2")
    Assert.assertTrue(build["event_data_99"] == "default-value")
    Assert.assertTrue(build["count"] == 10)
  }

  @Test
  fun `test merge group with group default event data fields`() {
    val group = FeatureUsageData().addPlace("EditorToolbar")
    val event = FeatureUsageData().addData("second", "value-2")
    val merged = FUStateUsagesLogger.mergeWithEventData(group, event)

    val build = merged!!.build()
    Assert.assertTrue(build.size == 2)
    Assert.assertTrue(build["place"] == "EditorToolbar")
    Assert.assertTrue(build["second"] == "value-2")
  }

  @Test
  fun `test merge group with event default event data fields`() {
    val group = FeatureUsageData().addData("first", "value-1")
    val event = FeatureUsageData().addPlace("EditorToolbar")
    val merged = FUStateUsagesLogger.mergeWithEventData(group, event)

    val build = merged!!.build()
    Assert.assertTrue(build.size == 2)
    Assert.assertTrue(build["first"] == "value-1")
    Assert.assertTrue(build["place"] == "EditorToolbar")
  }

  @Test
  fun `test copy group with default event data fields`() {
    val data = FeatureUsageData().addPlace("EditorToolbar")
    val copied = data.copy()

    val build = copied.build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build["place"] == "EditorToolbar")
  }
}