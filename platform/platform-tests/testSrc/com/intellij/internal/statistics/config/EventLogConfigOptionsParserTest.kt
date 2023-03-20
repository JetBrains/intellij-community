// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.config

import com.intellij.internal.statistic.config.bean.EventLogBucketRange
import com.intellij.internal.statistic.config.bean.EventLogSendConfiguration
import com.intellij.internal.statistic.config.eventLog.EventLogBuildType
import org.junit.Test

class EventLogConfigOptionsParserTest : EventLogConfigBaseParserTest() {
  private val GROUP_THRESHOLD = "groupDataThreshold"
  private val GROUP_ALERT_THRESHOLD = "groupAlertThreshold"
  private val DATA_THRESHOLD = "dataThreshold"

  private val ALL_OPTIONS: Set<String> = setOf(DATA_THRESHOLD, GROUP_THRESHOLD, GROUP_ALERT_THRESHOLD)

  private fun doTestOptions(options: String, expected: Map<String, String>) {
    val buildConfig = mapOf(
      EventLogBuildType.EAP to EventLogSendConfiguration(listOf(EventLogBucketRange(0, 256))),
      EventLogBuildType.RELEASE to EventLogSendConfiguration(listOf(EventLogBucketRange(0, 256)))
    )
    val config = """
{
  "productCode": "IU",
  "versions": [{
    "majorBuildVersionBorders": { "from": "2019.1" },
    "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
    "endpoints": {
      "send": "https://send/endpoint",
      "metadata": "https://metadata/endpoint/"
    }
    """ + (if (options.isNotEmpty()) ", $options" else options) + """
  }]
}"""
    val notExistingOptions = ALL_OPTIONS - expected.keys
    doTest(config, existingConfig = buildConfig, existingOptions = expected, notExistingOptions = notExistingOptions)
  }

  @Test
  fun `test parse no options fragment`() {
    doTestOptions("", emptyMap())
  }

  @Test
  fun `test parse option fragment without options`() {
    doTestOptions("""
"options": {}
""", emptyMap())
  }

  @Test
  fun `test parse single string option value`() {
    doTestOptions("""
"options": {
  "dataThreshold": "16000"
}
""", mapOf(DATA_THRESHOLD to "16000"))
  }

  @Test
  fun `test parse single integer option value`() {
    doTestOptions("""
"options": {
  "dataThreshold": 16000
}
""", mapOf(DATA_THRESHOLD to "16000"))
  }

  @Test
  fun `test parse single boolean option value`() {
    doTestOptions("""
"options": {
  "dataThreshold": true
}
""", mapOf(DATA_THRESHOLD to "true"))
  }

  @Test
  fun `test parse multiple options`() {
    doTestOptions("""
"options": {
  "dataThreshold": 16000,
  "groupDataThreshold": 8000
}
""", mapOf(DATA_THRESHOLD to "16000", GROUP_THRESHOLD to "8000"))
  }

  @Test
  fun `test parse all available options`() {
    doTestOptions("""
"options": {
  "dataThreshold": 16000,
  "groupDataThreshold": 8000,
  "groupAlertThreshold": 4000
}
""", mapOf(DATA_THRESHOLD to "16000", GROUP_THRESHOLD to "8000", GROUP_ALERT_THRESHOLD to "4000"))
  }

  @Test
  fun `test parse unknown options`() {
    doTestOptions("""
"options": {
  "foo": 123,
  "bar": 456,
  "baz": 789
}
""", mapOf("foo" to "123", "bar" to "456", "baz" to "789"))
  }
}
