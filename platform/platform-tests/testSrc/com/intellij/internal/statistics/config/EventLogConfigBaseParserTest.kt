// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.config

import com.intellij.internal.statistic.config.EventLogConfigParserException
import com.intellij.internal.statistic.config.EventLogExternalSettings
import com.intellij.internal.statistic.config.bean.EventLogBucketRange
import com.intellij.internal.statistic.config.bean.EventLogSendConfiguration
import com.intellij.internal.statistic.eventLog.EventLogBuildType
import org.junit.Assert
import java.io.BufferedReader
import java.io.StringReader

private const val DEFAULT_VERSION = "2019.2"

abstract class EventLogConfigBaseParserTest {

  @Suppress("SameParameterValue")
  protected fun doTestError(config: String) {
    var exception: Exception? = null
    try {
      doTest(config)
    }
    catch (e: Exception) {
      exception = e
    }
    Assert.assertNotNull(exception)
    Assert.assertTrue(exception is EventLogConfigParserException)
  }

  protected fun doTest(config: String,
                       existingConfig: Map<EventLogBuildType, EventLogSendConfiguration> = emptyMap(),
                       existingEndpoints: Map<String, String> = emptyMap(),
                       notExistingConfig: Set<EventLogBuildType> = emptySet(),
                       notExistingEndpoints: Set<String> = emptySet()) {
    val reader = BufferedReader(StringReader(config))
    val settings = EventLogExternalSettings.parseSendSettings(reader, DEFAULT_VERSION)
    Assert.assertNotNull(settings)

    val sendEnabled = existingConfig.isNotEmpty()
    Assert.assertEquals(sendEnabled, settings.isSendEnabled)

    for (type in existingConfig.keys) {
      val actual = settings.getConfiguration(type)
      Assert.assertNotNull(actual)
      Assert.assertEquals(existingConfig[type], actual)
    }

    for (type in notExistingConfig) {
      Assert.assertNull(settings.getConfiguration(type))
    }

    for (endpoint in existingEndpoints) {
      Assert.assertEquals(endpoint.value, settings.getEndpoint(endpoint.key))
    }

    for (endpoint in notExistingEndpoints) {
      Assert.assertNull(settings.getEndpoint(endpoint))
    }
  }

  fun newConfig(buckets: List<EventLogBucketRange>): EventLogSendConfiguration {
    return EventLogSendConfiguration(buckets)
  }
}