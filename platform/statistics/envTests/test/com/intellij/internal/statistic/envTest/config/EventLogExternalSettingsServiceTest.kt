// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.envTest.config

import com.intellij.internal.statistic.config.EventLogOptions.MACHINE_ID_SALT
import com.intellij.internal.statistic.config.EventLogOptions.MACHINE_ID_SALT_REVISION
import com.intellij.internal.statistic.envTest.StatisticsServiceBaseTest
import com.intellij.internal.statistic.envTest.upload.TestEventLogUploadSettingsClient
import com.intellij.internal.statistic.eventLog.connection.EventLogSettingsClient
import java.util.concurrent.TimeUnit

internal class EventLogExternalSettingsServiceTest: StatisticsServiceBaseTest() {
  fun `test load and cache external settings`() {
    val settingsClient = configureDynamicConfig(TimeUnit.HOURS.toMillis(1))

    val metadataProductUrl = loadMetadata(settingsClient.provideMetadataProductUrl())
    Thread.sleep(1000)
    assertMetadata(settingsClient.provideMetadataProductUrl(), metadataProductUrl)
    Thread.sleep(1000)
    assertMetadata(settingsClient.provideMetadataProductUrl(), metadataProductUrl)
  }

  fun `test cached external settings are invalidated`() {
    val settingsClient = configureDynamicConfig(10)

    var metadataProductUrl = loadMetadata(settingsClient.provideMetadataProductUrl())
    Thread.sleep(1000)
    metadataProductUrl = assertNewMetadata(settingsClient.provideMetadataProductUrl(), metadataProductUrl)
    Thread.sleep(1000)
    assertNewMetadata(settingsClient.provideMetadataProductUrl(), metadataProductUrl)
  }

  fun `test load options from external settings`() {
    val settingsClient = configureDynamicConfig(TimeUnit.HOURS.toMillis(1))

    val options = settingsClient.provideOptions()
    assertEquals(options["dataThreshold"], "16000")
    assertEquals(options["groupDataThreshold"], "8000")
    assertEquals(options["groupAlertThreshold"], "4000")
  }

  fun `test load salt and id revisions from external settings`() {
    val settingsClient = configureDynamicConfig(TimeUnit.HOURS.toMillis(1))

    val options = settingsClient.provideOptions()
    assertEquals(options[MACHINE_ID_SALT], "test_salt")
    assertEquals(options[MACHINE_ID_SALT_REVISION], "1")
  }

  private fun configureDynamicConfig(configCacheTimeoutMs: Long): EventLogSettingsClient {
    return TestEventLogUploadSettingsClient(container.getBaseUrl("config/dynamic_config.php").toString(), configCacheTimeoutMs)
  }

  private fun loadMetadata(metadata: String?): String {
    assertNotNull("Cannot retrieve metadata url", metadata)
    return metadata!!
  }

  private fun assertNewMetadata(metadata: String?, previous: String?): String {
    assertNotNull("Cannot retrieve metadata url", metadata)
    previous?.let {
      val newMetadata = parseMetadata(metadata)
      val oldMetadata = parseMetadata(previous)
      assertTrue("Metadata did not change: $it", newMetadata != oldMetadata)
    }
    return metadata!!
  }

  private fun assertMetadata(metadata: String?, previous: String?) {
    assertNotNull("Cannot retrieve metadata url", metadata)
    previous?.let {
      val newMetadata = parseMetadata(metadata)
      val oldMetadata = parseMetadata(previous)
      assertEquals("Metadata changed but should stay the same", oldMetadata, newMetadata)
    }
  }

  private fun parseMetadata(metadataUrl: String?): String {
    assertTrue("Wrong format of the metadata url: $metadataUrl", metadataUrl!!.contains("metadata/"))

    val start = metadataUrl.indexOf("metadata/") + "metadata/".length
    val end = metadataUrl.lastIndexOf("/")
    assertTrue("Wrong format of the metadata url: $metadataUrl", end >= 0 && start >= 0 && end > start)
    return metadataUrl.substring(start, end)
  }
}