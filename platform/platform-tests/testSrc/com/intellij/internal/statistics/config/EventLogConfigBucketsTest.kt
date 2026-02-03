// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.config

import com.intellij.internal.statistic.config.eventLog.EventLogBuildType
import com.intellij.internal.statistic.config.eventLog.EventLogBuildType.*
import com.jetbrains.fus.reporting.model.config.v4.ConfigurationBucketRange
import org.junit.Test

class EventLogConfigBucketsTest : EventLogConfigBaseParserTest() {

  private fun doTestBuckets(buckets: String, vararg expected: ConfigurationBucketRange) {
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
    val existingConfig: Map<EventLogBuildType, List<ConfigurationBucketRange>> = if (expected.isNotEmpty()) hashMapOf(RELEASE to expected.toList()) else emptyMap()
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
""", ConfigurationBucketRange(0, 256))
  }

  @Test
  fun `test parse bucket range without from`() {
    doTestBuckets("""
"to":128
""", ConfigurationBucketRange(0, 128))
  }

  @Test
  fun `test parse bucket range without to`() {
    doTestBuckets("""
"from":64
""", ConfigurationBucketRange(64, Int.MAX_VALUE))
  }
}