// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.config

import com.intellij.internal.statistic.config.eventLog.EventLogBuildType
import com.intellij.internal.statistics.TestEventLogUploadSettingsClient
import com.intellij.internal.statistics.TestHttpServerProcessing
import com.jetbrains.fus.reporting.model.config.v4.ConfigurationBucketRange
import org.junit.Assert

abstract class EventLogConfigBaseParserTest {
  protected fun doTest(
    config: String,
    existingConfig: Map<EventLogBuildType, List<ConfigurationBucketRange>> = emptyMap(),
    existingEndpoints: Map<String, String> = emptyMap(),
    notExistingConfig: Set<EventLogBuildType> = emptySet(),
    notExistingEndpoints: Set<String> = emptySet(),
    existingOptions: Map<String, String> = emptyMap(),
    notExistingOptions: Set<String> = emptySet(),
  ) {
    val serverProcessing = TestHttpServerProcessing(config)
    try {
      serverProcessing.serverStart()
      val configurationClient = TestEventLogUploadSettingsClient(serverProcessing.getUrl())

      val sendEnabled = existingConfig.isNotEmpty()
      Assert.assertEquals(sendEnabled, configurationClient.isSendEnabled())

      for (type in existingConfig.keys) {
        val actual = configurationClient.provideBucketRanges(type).firstOrNull()
        Assert.assertNotNull(actual)
        Assert.assertEquals(existingConfig[type]?.firstOrNull(), actual)
      }

      for (type in notExistingConfig) {
        Assert.assertNull(configurationClient.provideBucketRanges(type).firstOrNull())
      }

      for (endpoint in existingEndpoints) {
        Assert.assertEquals(endpoint.value, configurationClient.provideEndpointValue(endpoint.key))
      }

      for (endpoint in notExistingEndpoints) {
        Assert.assertNull(configurationClient.provideEndpointValue(endpoint))
      }

      val options = configurationClient.provideOptions()
      for (option in existingOptions) {
        Assert.assertEquals(option.value, options[option.key])
      }

      for (option in notExistingOptions) {
        Assert.assertNull(options[option])
      }
    }
    finally {
      serverProcessing.serverStop()
    }
  }
}