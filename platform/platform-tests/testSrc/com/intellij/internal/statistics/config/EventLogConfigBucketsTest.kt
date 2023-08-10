// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.config

import com.intellij.internal.statistic.config.bean.EventLogBucketRange
import com.intellij.internal.statistic.config.bean.EventLogSendConfiguration
import com.intellij.internal.statistic.config.eventLog.EventLogBuildType
import com.intellij.internal.statistic.config.eventLog.EventLogBuildType.*
import org.junit.Test

class EventLogConfigBucketsTest : EventLogConfigBaseParserTest() {

  private fun doTestBuckets(buckets: String, vararg expected: EventLogBucketRange) {
    val config = """
{
  "productCode": "IU",
  "versions": [
    {
      "majorBuildVersionBorders": {
        "from": "2019.2"
      },
      "releaseFilters": [{
        "releaseType": "RELEASE"""" + (if (buckets.isNotEmpty()) "," else "") + """
        """ + buckets + """
      }],
      "endpoints": {
        "send": "https://send/endpoint",
        "metadata": "https://metadata/endpoint/",
        "dictionary": "https://dictionary/endpoint/"
      }
    }
  ]
}"""
    val existingConfig: Map<EventLogBuildType, EventLogSendConfiguration> = if (expected.isNotEmpty()) hashMapOf(RELEASE to EventLogSendConfiguration(expected.toList())) else emptyMap()
    val notExistingConfig: Set<EventLogBuildType> = if (expected.isNotEmpty()) hashSetOf(EAP, UNKNOWN) else hashSetOf(EAP, UNKNOWN, RELEASE)
    val endpoints = hashMapOf(
      "send" to "https://send/endpoint",
      "metadata" to "https://metadata/endpoint/",
      "dictionary" to "https://dictionary/endpoint/"
    )
    doTest(config, existingConfig = existingConfig, notExistingConfig = notExistingConfig, existingEndpoints = endpoints)
  }

  @Test
  fun `test parse empty bucket`() {
    doTestBuckets("""""")
  }

  @Test
  fun `test parse single bucket range`() {
    doTestBuckets("""
"from":0, "to":256
""", EventLogBucketRange(0, 256))
  }

  @Test
  fun `test parse bucket range without from`() {
    doTestBuckets("""
"to":128
""", EventLogBucketRange(0, 128))
  }

  @Test
  fun `test parse bucket range without to`() {
    doTestBuckets("""
"from":64
""", EventLogBucketRange(64, Int.MAX_VALUE))
  }
}