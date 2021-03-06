// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.collectors.fus.ActionCustomPlaceAllowlist
import com.intellij.internal.statistic.collectors.fus.ActionPlaceHolder
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Version
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.registerExtension
import org.junit.Assert
import org.junit.Test

class FeatureUsageDataTest : HeavyPlatformTestCase() {

  @Test
  fun `test empty data`() {
    val build = FeatureUsageData().build()
    Assert.assertTrue(build.isEmpty())
  }

  @Test
  fun `test add string data`() {
    val build = FeatureUsageData().addData("key", "my-value").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build["key"] == "my-value")
  }

  @Test
  fun `test add int data`() {
    val build = FeatureUsageData().addData("key", 99).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build["key"] == 99)
  }

  @Test
  fun `test add long data`() {
    val value: Long = 99
    val build = FeatureUsageData().addData("key", value).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build["key"] == 99L)
  }

  @Test
  fun `test add boolean data`() {
    val build = FeatureUsageData().addData("key", true).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build["key"] == true)
  }

  @Test
  fun `test add int array data`() {
    val build = FeatureUsageData().addData("key", listOf("1", "2", "3")).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertEquals(build["key"], listOf("1", "2", "3"))
  }

  @Test
  fun `test add null place`() {
    val build = FeatureUsageData().addPlace(null).build()
    Assert.assertTrue(build.isEmpty())
  }

  @Test
  fun `test add empty place`() {
    val build = FeatureUsageData().addPlace("").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("place"))
    Assert.assertTrue(build["place"] == ActionPlaces.UNKNOWN)
  }

  @Test
  fun `test add common place`() {
    val build = FeatureUsageData().addPlace("TestTreeViewToolbar").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("place"))
    Assert.assertTrue(build["place"] == "TestTreeViewToolbar")
  }

  @Test
  fun `test add common popup place`() {
    val build = FeatureUsageData().addPlace("FavoritesPopup").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("place"))
    Assert.assertTrue(build["place"] == "FavoritesPopup")
  }

  @Test
  fun `test add custom popup place`() {
    val build = FeatureUsageData().addPlace("popup@my-custom-popup").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("place"))
    Assert.assertTrue(build["place"] == ActionPlaces.POPUP)
  }

  @Test
  fun `test add custom place`() {
    val build = FeatureUsageData().addPlace("my-custom-popup").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("place"))
    Assert.assertTrue(build["place"] == ActionPlaces.UNKNOWN)
  }

  @Test
  fun `test add anonymized path`() {
    val path = "/my/path/to/smth"
    val build = FeatureUsageData().addAnonymizedPath(path).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("file_path"))
    Assert.assertTrue(build["file_path"] != path)
    Assert.assertTrue(build["file_path"] != EventLogConfiguration.getOrCreate("ABC").anonymize(path))
    Assert.assertTrue(build["file_path"] == EventLogConfiguration.getOrCreate("FUS").anonymize(path))
  }

  @Test
  fun `test add anonymized path with another recorder`() {
    val path = "/my/path/to/smth"
    val build = FeatureUsageData("ABC").addAnonymizedPath(path).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("file_path"))
    Assert.assertTrue(build["file_path"] != path)
    Assert.assertTrue(build["file_path"] != EventLogConfiguration.getOrCreate("FUS").anonymize(path))
    Assert.assertTrue(build["file_path"] == EventLogConfiguration.getOrCreate("ABC").anonymize(path))
  }

  @Test
  fun `test add undefined path`() {
    val build = FeatureUsageData().addAnonymizedPath(null).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("file_path"))
    Assert.assertTrue(build["file_path"] == "undefined")
  }

  @Test
  fun `test add anonymized id`() {
    val id = "item-id"
    val build = FeatureUsageData().addAnonymizedId(id).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("anonymous_id"))
    Assert.assertTrue(build["anonymous_id"] != id)
    Assert.assertTrue(build["anonymous_id"] != EventLogConfiguration.getOrCreate("ABC").anonymize(id))
    Assert.assertTrue(build["anonymous_id"] == EventLogConfiguration.getOrCreate("FUS").anonymize(id))
  }

  @Test
  fun `test add anonymized id with another recorder`() {
    val id = "item-id"
    val build = FeatureUsageData("ABC").addAnonymizedId(id).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("anonymous_id"))
    Assert.assertTrue(build["anonymous_id"] != id)
    Assert.assertTrue(build["anonymous_id"] != EventLogConfiguration.getOrCreate("FUS").anonymize(id))
    Assert.assertTrue(build["anonymous_id"] == EventLogConfiguration.getOrCreate("ABC").anonymize(id))
  }

  @Test
  fun `test add null obj version`() {
    val build = FeatureUsageData().addVersion(null).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "unknown.format")
  }

  @Test
  fun `test add null version`() {
    val build = FeatureUsageData().addVersionByString(null).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "unknown")
  }

  @Test
  fun `test add obj version`() {
    val build = FeatureUsageData().addVersion(Version(3, 5, 0)).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "3.5")
  }

  @Test
  fun `test add version`() {
    val build = FeatureUsageData().addVersionByString("5.11.0").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "5.11")
  }

  @Test
  fun `test add obj version with bugfix`() {
    val build = FeatureUsageData().addVersion(Version(3, 5, 9)).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "3.5")
  }

  @Test
  fun `test add version with bugfix`() {
    val build = FeatureUsageData().addVersionByString("5.11.98").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "5.11")
  }

  @Test
  fun `test add invalid version`() {
    val build = FeatureUsageData().addVersionByString("2018-11").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "2018.0")
  }

  @Test
  fun `test add empty version`() {
    val build = FeatureUsageData().addVersionByString("").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "unknown.format")
  }

  @Test
  fun `test add version with snapshot`() {
    val build = FeatureUsageData().addVersionByString("1.4-SNAPSHOT").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "1.4")
  }

  @Test
  fun `test add version with suffix`() {
    val build = FeatureUsageData().addVersionByString("2.5.0.BUILD-2017091412345").build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("version"))
    Assert.assertTrue(build["version"] == "2.5")
  }

  @Test
  fun `test add version with letters`() {
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

  @Test
  fun `test add registered custom place`() {
    registerCustomActionPlace("MY_REGISTERED_PLACE")

    assertPlaceAllowed("MY_REGISTERED_PLACE")
  }

  @Test
  fun `test add multiple registered custom places`() {
    registerCustomActionPlace("FIRST;SECOND")

    assertPlaceAllowed("FIRST")
    assertPlaceAllowed("SECOND")
    assertPlaceUnknown("THIRD")
  }

  @Test
  fun `test add multiple extensions with registered custom places`() {
    registerCustomActionPlace("MULTIPLE_EXTENSIONS_FIRST;MULTIPLE_EXTENSIONS_SECOND")

    assertPlaceAllowed("MULTIPLE_EXTENSIONS_FIRST")
    assertPlaceAllowed("MULTIPLE_EXTENSIONS_SECOND")
    assertPlaceUnknown("MULTIPLE_EXTENSIONS_FORTH")

    registerCustomActionPlace("MULTIPLE_EXTENSIONS_THIRD")
    assertPlaceAllowed("MULTIPLE_EXTENSIONS_THIRD")
    assertPlaceUnknown("MULTIPLE_EXTENSIONS_FORTH")
  }

  private fun assertPlaceAllowed(place: String) {
    val build = FeatureUsageData().addPlace(place).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("place"))
    Assert.assertTrue(build["place"] == place)
  }

  private fun assertPlaceUnknown(place: String) {
    val build = FeatureUsageData().addPlace(place).build()
    Assert.assertTrue(build.size == 1)
    Assert.assertTrue(build.containsKey("place"))
    Assert.assertTrue(build["place"] == ActionPlaces.UNKNOWN)
  }

  private fun registerCustomActionPlace(place: String) {
    val extension = ActionCustomPlaceAllowlist()
    extension.places = place
    ApplicationManager.getApplication().registerExtension(ActionPlaceHolder.EP_NAME, extension, testRootDisposable)
  }
}