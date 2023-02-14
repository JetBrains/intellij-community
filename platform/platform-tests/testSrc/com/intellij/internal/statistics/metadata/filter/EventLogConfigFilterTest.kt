// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.metadata.filter

import com.intellij.internal.statistic.config.EventLogExternalSettings
import com.intellij.internal.statistic.config.eventLog.EventLogBuildType.RELEASE
import com.intellij.internal.statistic.eventLog.filters.LogEventBucketsFilter
import com.intellij.internal.statistics.StatisticsTestEventFactory.newEvent
import org.junit.Assert
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class EventLogConfigFilterTest {
  private fun doTestNoFilter(filters: String) {
    Assert.assertNull(toFilterInternal(filters))
  }

  private fun toFilter(filters: String): LogEventBucketsFilter {
    return toFilterInternal(filters)!!
  }

  private fun toFilterInternal(filters: String): LogEventBucketsFilter? {
    val reader = BufferedReader(StringReader("""
{
  "productCode": "IU",
  "versions": [
    {
      "majorBuildVersionBorders": {
        "from": "2019.2"
      },
      "releaseFilters": [""" + filters + """],
      "endpoints": {
        "send": "https://send/endpoint",
        "metadata": "https://metadata/endpoint/",
        "dictionary": "https://dictionary/endpoint/"
      }
    }
  ]
}""".trimIndent()))
    val settings = EventLogExternalSettings.parseSendSettings(reader, "2019.2")
    Assert.assertNotNull(settings)
    val configuration = settings.getConfiguration(RELEASE)
    if (configuration == null) {
      return null
    }
    return LogEventBucketsFilter(configuration.buckets)
  }

  @Test
  fun `test filter by empty bucket`() {
    doTestNoFilter(filters = "")
  }

  @Test
  fun `test filter by empty bucket range`() {
    doTestNoFilter(filters = """
{}
""")
  }

  @Test
  fun `test filter by empty bucket ranges`() {
    doTestNoFilter(filters = """
{}, {}, {}
""")
  }

  @Test
  fun `test filter by single bucket range`() {
    val filter = toFilter(filters = """
{"releaseType": "RELEASE", "from":0, "to":256}
""")
    Assert.assertFalse(filter.accepts(newEvent(bucket = "-10")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "-1")))

    Assert.assertTrue(filter.accepts(newEvent(bucket = "0")))
    Assert.assertTrue(filter.accepts(newEvent(bucket = "2")))
    Assert.assertTrue(filter.accepts(newEvent(bucket = "32")))
    Assert.assertTrue(filter.accepts(newEvent(bucket = "128")))

    Assert.assertFalse(filter.accepts(newEvent(bucket = "512")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "1024")))
  }

  @Test
  fun `test filter by multiple bucket ranges`() {
    val filter = toFilter(filters = """
{"releaseType": "RELEASE", "from":0, "to":32}, 
{"releaseType": "RELEASE", "from":64, "to":128}
""")
    Assert.assertFalse(filter.accepts(newEvent(bucket = "-10")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "-1")))

    Assert.assertTrue(filter.accepts(newEvent(bucket = "0")))
    Assert.assertTrue(filter.accepts(newEvent(bucket = "2")))

    Assert.assertFalse(filter.accepts(newEvent(bucket = "32")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "50")))

    Assert.assertTrue(filter.accepts(newEvent(bucket = "64")))
    Assert.assertTrue(filter.accepts(newEvent(bucket = "100")))

    Assert.assertFalse(filter.accepts(newEvent(bucket = "128")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "512")))
  }

  @Test
  fun `test filters with first from multiple release types`() {
    val filter = toFilter(filters = """
{"releaseType": "RELEASE", "from":0, "to":32}, 
{"releaseType": "EAP", "from":64, "to":128}
""")
    Assert.assertFalse(filter.accepts(newEvent(bucket = "-10")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "-1")))

    Assert.assertTrue(filter.accepts(newEvent(bucket = "0")))
    Assert.assertTrue(filter.accepts(newEvent(bucket = "2")))

    Assert.assertFalse(filter.accepts(newEvent(bucket = "32")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "50")))

    Assert.assertFalse(filter.accepts(newEvent(bucket = "64")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "100")))

    Assert.assertFalse(filter.accepts(newEvent(bucket = "128")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "512")))
  }

  @Test
  fun `test filters with second from multiple release types`() {
    val filter = toFilter(filters = """
{"releaseType": "EAP", "from":0, "to":32}, 
{"releaseType": "RELEASE", "from":64, "to":128}
""")
    Assert.assertFalse(filter.accepts(newEvent(bucket = "-10")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "-1")))

    Assert.assertFalse(filter.accepts(newEvent(bucket = "0")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "2")))

    Assert.assertFalse(filter.accepts(newEvent(bucket = "32")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "50")))

    Assert.assertTrue(filter.accepts(newEvent(bucket = "64")))
    Assert.assertTrue(filter.accepts(newEvent(bucket = "100")))

    Assert.assertFalse(filter.accepts(newEvent(bucket = "128")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "512")))
  }

  @Test
  fun `test filters with multiple release types`() {
    val filter = toFilter(filters = """
{"releaseType": "RELEASE", "from":0, "to":32}, 
{"releaseType": "EAP", "from":32, "to":64}, 
{"releaseType": "RELEASE", "from":64, "to":128}
""")
    Assert.assertFalse(filter.accepts(newEvent(bucket = "-10")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "-1")))

    Assert.assertTrue(filter.accepts(newEvent(bucket = "0")))
    Assert.assertTrue(filter.accepts(newEvent(bucket = "2")))

    Assert.assertFalse(filter.accepts(newEvent(bucket = "32")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "50")))

    Assert.assertTrue(filter.accepts(newEvent(bucket = "64")))
    Assert.assertTrue(filter.accepts(newEvent(bucket = "100")))

    Assert.assertFalse(filter.accepts(newEvent(bucket = "128")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "512")))
  }

  @Test
  fun `test filter by bucket range without from`() {
    val filter = toFilter(filters = """
{"releaseType": "RELEASE", "to":128}
""")
    Assert.assertFalse(filter.accepts(newEvent(bucket = "-10")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "-1")))

    Assert.assertTrue(filter.accepts(newEvent(bucket = "0")))
    Assert.assertTrue(filter.accepts(newEvent(bucket = "2")))
    Assert.assertTrue(filter.accepts(newEvent(bucket = "32")))
    Assert.assertTrue(filter.accepts(newEvent(bucket = "64")))

    Assert.assertFalse(filter.accepts(newEvent(bucket = "128")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "256")))
  }

  @Test
  fun `test filter by bucket range without to`() {
    val filter = toFilter(filters = """
{"releaseType": "RELEASE", "from":64}
""")
    Assert.assertFalse(filter.accepts(newEvent(bucket = "-1")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "0")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "2")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "32")))

    Assert.assertTrue(filter.accepts(newEvent(bucket = "128")))
    Assert.assertTrue(filter.accepts(newEvent(bucket = "256")))
  }

  @Test
  fun `test filter by duplicate bucket ranges`() {
    val filter = toFilter(filters = """
{"releaseType": "RELEASE", "from": 3, "to": 64}, 
{"releaseType": "RELEASE", "from":1, "to": 2}, 
{"releaseType": "RELEASE", "from":3, "to": 4}, 
{"releaseType": "RELEASE", "from":4, "to": 5}
""")
    Assert.assertFalse(filter.accepts(newEvent(bucket = "0")))

    Assert.assertTrue(filter.accepts(newEvent(bucket = "1")))

    Assert.assertFalse(filter.accepts(newEvent(bucket = "2")))

    Assert.assertTrue(filter.accepts(newEvent(bucket = "3")))
    Assert.assertTrue(filter.accepts(newEvent(bucket = "4")))
    Assert.assertTrue(filter.accepts(newEvent(bucket = "32")))

    Assert.assertFalse(filter.accepts(newEvent(bucket = "128")))
    Assert.assertFalse(filter.accepts(newEvent(bucket = "256")))
  }
}