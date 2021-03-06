// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.envTest.config

import com.intellij.internal.statistic.envTest.StatisticsServiceBaseTest
import com.intellij.internal.statistic.envTest.upload.RECORDER_ID
import com.intellij.internal.statistic.envTest.upload.TestEventLogApplicationInfo
import com.intellij.internal.statistic.eventLog.EventLogConfigOptionsService.*
import com.intellij.internal.statistic.eventLog.connection.EventLogUploadSettingsService
import junit.framework.TestCase
import java.util.concurrent.TimeUnit

internal class EventLogExternalSettingsServiceTest : StatisticsServiceBaseTest() {
  fun `test load and cache external settings`() {
    val settings = configureDynamicConfig(TimeUnit.HOURS.toMillis(1))

    val metadata = loadMetadata(settings.metadataProductUrl)
    Thread.sleep(1000)
    assertMetadata(settings.metadataProductUrl, metadata)
    Thread.sleep(1000)
    assertMetadata(settings.metadataProductUrl, metadata)
  }

  fun `test cached external settings are invalidated`() {
    val settings = configureDynamicConfig(10)

    var metadata = loadMetadata(settings.metadataProductUrl)
    Thread.sleep(1000)
    metadata = assertNewMetadata(settings.metadataProductUrl, metadata)
    Thread.sleep(1000)
    assertNewMetadata(settings.metadataProductUrl, metadata)
  }

  fun `test load options from external settings`() {
    val settings = configureDynamicConfig(TimeUnit.HOURS.toMillis(1))

    TestCase.assertEquals(settings.getOptionValue("dataThreshold"), "16000")
    TestCase.assertEquals(settings.getOptionValue("groupDataThreshold"), "8000")
    TestCase.assertEquals(settings.getOptionValue("groupAlertThreshold"), "4000")
  }

  fun `test load salt and id revisions from external settings`() {
    val settings = configureDynamicConfig(TimeUnit.HOURS.toMillis(1))

    TestCase.assertEquals(settings.getOptionValue(MACHINE_ID_SALT), "test_salt")
    TestCase.assertEquals(settings.getOptionValue(MACHINE_ID_SALT_REVISION), "1")
  }

  private fun configureDynamicConfig(configCacheTimeoutMs: Long): EventLogUploadSettingsService {
    val applicationInfo = TestEventLogApplicationInfo(RECORDER_ID, container.getBaseUrl("config/dynamic_config.php").toString())
    return EventLogUploadSettingsService(RECORDER_ID, applicationInfo, configCacheTimeoutMs)
  }

  private fun loadMetadata(metadata: String?): String {
    TestCase.assertNotNull("Cannot retrieve metadata url", metadata)
    return metadata!!
  }

  private fun assertNewMetadata(metadata: String?, previous: String?): String {
    TestCase.assertNotNull("Cannot retrieve metadata url", metadata)
    previous?.let {
      val newMetadata = parseMetadata(metadata)
      val oldMetadata = parseMetadata(previous)
      TestCase.assertTrue("Metadata did not change: $it", newMetadata != oldMetadata)
    }
    return metadata!!
  }

  private fun assertMetadata(metadata: String?, previous: String?) {
    TestCase.assertNotNull("Cannot retrieve metadata url", metadata)
    previous?.let {
      val newMetadata = parseMetadata(metadata)
      val oldMetadata = parseMetadata(previous)
      TestCase.assertEquals("Metadata changed but should stay the same", oldMetadata, newMetadata)
    }
  }

  private fun parseMetadata(metadataUrl: String?): String {
    TestCase.assertTrue("Wrong format of the metadata url: $metadataUrl", metadataUrl!!.contains("metadata/"))

    val start = metadataUrl.indexOf("metadata/") + "metadata/".length
    val end = metadataUrl.lastIndexOf("/")
    TestCase.assertTrue("Wrong format of the metadata url: $metadataUrl", end >= 0 && start >= 0 && end > start)
    return metadataUrl.substring(start, end)
  }
}