// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.envTest.scheme

import com.intellij.internal.statistic.envTest.StatisticsServiceBaseTest
import com.intellij.internal.statistic.eventLog.EventLogBuild.EVENT_LOG_BUILD_PRODUCER
import com.intellij.internal.statistic.eventLog.connection.EventLogBasicConnectionSettings
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupFilterRules
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupFilterRules.BuildRange
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupFilterRules.VersionRange
import com.intellij.internal.statistic.eventLog.connection.metadata.EventGroupsFilterRules
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataLoadException
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUtils
import junit.framework.TestCase

private val SETTINGS = EventLogBasicConnectionSettings("Test IntelliJ")

internal class EventLogMetadataServiceTest : StatisticsServiceBaseTest() {

  fun `test load metadata succeed`() {
    val metadataUrl = getMetadataUrl("IC.json")
    val content = EventLogMetadataUtils.loadMetadataFromServer(metadataUrl, SETTINGS)
    TestCase.assertNotNull(content)
    TestCase.assertTrue(content.isNotEmpty())
  }

  fun `test load metadata fail because product code does not exists`() {
    var exception: Exception? = null
    try {
      val metadataUrl = getMetadataUrl("AB.json")
      EventLogMetadataUtils.loadMetadataFromServer(metadataUrl, SETTINGS)
    }
    catch (e: Exception) {
      exception = e
    }
    TestCase.assertNotNull(exception)
    TestCase.assertTrue(exception is EventLogMetadataLoadException)
  }

  fun `test get last modified metadata`() {
    val metadataUrl = getMetadataUrl()
    val lastModified = EventLogMetadataUtils.lastModifiedMetadata(metadataUrl, SETTINGS)
    TestCase.assertEquals(1589968216000, lastModified)
  }

  fun `test load and parse metadata`() {
    val expected = hashMapOf(
      "test.group" to EventGroupFilterRules(emptyList(), listOf(VersionRange.create("3", null))),
      "second.test.group" to EventGroupFilterRules(listOf(BuildRange.create("191.12345", null, EVENT_LOG_BUILD_PRODUCER)), emptyList())
    )

    val metadataUrl = getMetadataUrl()
    val actual = EventLogMetadataUtils.loadAndParseGroupsFilterRules(metadataUrl, SETTINGS)
    TestCase.assertEquals(EventGroupsFilterRules.create(expected, EVENT_LOG_BUILD_PRODUCER), actual)
  }

  fun `test failed loading and parsing metadata because url is unreachable`() {
    val metadataUrl = getMetadataUrl("AB.json")
    val actual = EventLogMetadataUtils.loadAndParseGroupsFilterRules(metadataUrl, SETTINGS)
    TestCase.assertNotNull(actual)
    TestCase.assertTrue(actual.isEmpty)
  }

  fun `test failed loading and parsing metadata because its invalid`() {
    val metadataUrl = getMetadataUrl("invalid-metadata.json")
    val actual = EventLogMetadataUtils.loadAndParseGroupsFilterRules(metadataUrl, SETTINGS)
    TestCase.assertNotNull(actual)
    TestCase.assertTrue(actual.isEmpty)
  }

  private fun getMetadataUrl(path: String? = null): String {
    return container.getBaseUrl("metadata/${path ?: "with-last-modified.php"}").toString()
  }
}