// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.config

import com.intellij.internal.statistic.config.bean.EventLogBucketRange
import com.intellij.internal.statistic.config.bean.EventLogSendConfiguration
import com.intellij.internal.statistic.config.eventLog.EventLogBuildType
import com.intellij.internal.statistic.config.eventLog.EventLogBuildType.*
import org.junit.Test

private val BUILD_TYPES = setOf(RELEASE, EAP, UNKNOWN)
private val ENDPOINTS = setOf("send", "metadata", "dictionary")

class EventLogConfigParserTest : EventLogConfigBaseParserTest() {

  private fun doTestVersions(versions: String,
                             existingConfig: Map<EventLogBuildType, EventLogSendConfiguration> = emptyMap(),
                             notExistingConfig: Set<EventLogBuildType> = emptySet(),
                             existingEndpoints: Map<String, String> = emptyMap(),
                             notExistingEndpoints: Set<String> = emptySet()) {
    doTest("""
{
  "productCode": "IU",
  "versions": [""" + versions + """]
}""", existingConfig, existingEndpoints, notExistingConfig, notExistingEndpoints)
  }

  private fun doTestEmptyVersions(versions: String) {
    doTestVersions(
      versions,
      notExistingConfig = setOf(RELEASE, EAP),
      notExistingEndpoints = setOf("send", "metadata", "dictionary")
    )
  }

  @Test
  fun `test parse no configurations`() {
    doTestEmptyVersions("")
  }

  @Test
  fun `test parse empty configuration`() {
    doTestEmptyVersions("{}")
  }

  @Test
  fun `test parse multiple empty configurations`() {
    doTestEmptyVersions("{}, {}")
  }

  @Test
  fun `test parse configuration without type`() {
    doTestEmptyVersions("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1"
  },
  "releaseFilters": [{
    "from": 0,
    "to": 256
  }]
}
""")
  }

  @Test
  fun `test parse configuration without buckets`() {
    doTestEmptyVersions("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1"
  },
  "releaseFilters": [{
    "releaseType": "RELEASE"
  }]
}
""")
  }

  @Test
  fun `test parse configuration with buckets`() {
    val configs = hashMapOf(
      RELEASE to newConfig(arrayListOf(EventLogBucketRange(32, 128)))
    )
    doTestVersions("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1"
  },
  "releaseFilters": [{
    "releaseType": "RELEASE",
    "from" : 32, 
    "to" : 128
  }]
}
""", configs, notExistingConfig = setOf(EAP))
  }

  @Test
  fun `test parse configuration with multiple buckets`() {
    val configs = hashMapOf(
      RELEASE to newConfig(arrayListOf(EventLogBucketRange(32, 128))),
      EAP to newConfig(arrayListOf(EventLogBucketRange(0, 64)))
    )
    doTestVersions("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1"
  },
  "releaseFilters": [{
    "releaseType": "RELEASE",
    "from" : 32,
    "to" : 128
  }, {
    "releaseType": "EAP",
    "from" : 0, 
    "to" : 64
  }]
}
""", configs)
  }

  @Test
  fun `test parse configuration with multiple buckets for the same type`() {
    val configs = hashMapOf(
      RELEASE to newConfig(arrayListOf(EventLogBucketRange (32, 128), EventLogBucketRange(0, 64)))
    )
    doTestVersions("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1"
  },
  "releaseFilters": [{
    "releaseType": "RELEASE",
    "from" : 32,
    "to" : 128
  },{
    "releaseType": "RELEASE",
    "from" : 0, 
    "to" : 64
  }]
}
""", configs, notExistingConfig = setOf(EAP))
  }

  @Test
  fun `test parse configuration with all types`() {
    val configs = hashMapOf(
      RELEASE to newConfig(arrayListOf(EventLogBucketRange(32, 128))),
      EAP to newConfig(arrayListOf(EventLogBucketRange(32, 128))),
      UNKNOWN to newConfig(arrayListOf(EventLogBucketRange(32, 128)))
    )
    doTestVersions("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1"
  },
  "releaseFilters": [{
    "releaseType": "ALL",
    "from" : 32,
    "to" : 128
  }]
}
""", configs)
  }

  @Test
  fun `test parse multiple filters`() {
    val configs = hashMapOf(
      RELEASE to newConfig(arrayListOf(EventLogBucketRange(32, 128))),
      EAP to newConfig(arrayListOf(EventLogBucketRange(0, 64)))
    )
    doTestVersions("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1"
  },
  "releaseFilters": [{
    "releaseType": "RELEASE",
    "from" : 32, 
    "to" : 128
  },{
    "releaseType": "EAP",
    "from" : 0,
    "to" : 64
  }]
}
""", configs)
  }

  @Test
  fun `test parse endpoints from version`() {
    val endpoints = hashMapOf(
      "send" to "https://send/endpoint",
      "metadata" to "https://metadata/endpoint/",
      "dictionary" to "https://dictionary/endpoint/"
    )
    val configs = hashMapOf(
      RELEASE to newConfig(arrayListOf(EventLogBucketRange(32, 128)))
    )
    doTestVersions("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1"
  },
  "releaseFilters": [{
    "releaseType": "RELEASE",
    "from" : 32,
    "to" : 128
  }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/",
    "dictionary": "https://dictionary/endpoint/"
  }
}""", configs, existingEndpoints = endpoints)
  }

  @Test
  fun `test parse empty endpoints`() {
    val configs = hashMapOf(
      RELEASE to newConfig(arrayListOf(EventLogBucketRange(32, 128)))
    )
    doTestVersions("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1"
  },
  "releaseFilters": [{
    "releaseType": "RELEASE",
    "from" : 32, 
    "to" : 128
  }],
  "endpoints": {
  }
}""", configs, notExistingEndpoints = setOf("send", "metadata", "dictionary"))
  }

  @Test
  fun `test parse no endpoints`() {
    val configs = hashMapOf(
      RELEASE to newConfig(arrayListOf(EventLogBucketRange(32, 128)))
    )
    doTestVersions("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1"
  },
  "releaseFilters": [{
    "releaseType": "RELEASE",
    "from" : 32, 
    "to" : 128
  }]
}""", configs, notExistingEndpoints = setOf("send", "metadata", "dictionary"))
  }

  @Test
  fun `test parse first version is applicable`() {
    val configs = hashMapOf(
      RELEASE to newConfig(arrayListOf(EventLogBucketRange(0, 64)))
    )
    doTestVersions("""
{
  "majorBuildVersionBorders": {
    "from": "2018.3"
  },
  "releaseFilters": [{
    "releaseType": "RELEASE",
    "from" : 0, 
    "to" : 64
  }]
},
{
  "majorBuildVersionBorders": {
    "from": "2019.1"
  },
  "releaseFilters": [{
    "releaseType": "EAP",
    "from" : 32, 
    "to" : 128
  }]
}""", configs, notExistingConfig = setOf(EAP))
  }

  @Test
  fun `test parse second version is applicable`() {
    val configs = hashMapOf(
      EAP to newConfig(arrayListOf(EventLogBucketRange(128, 256)))
    )
    doTestVersions("""
{
  "majorBuildVersionBorders": {
    "from": "2019.3"
  },
  "releaseFilters": [{
    "releaseType": "RELEASE",
    "from" : 0, 
    "to" : 64
  }]
},
{
  "majorBuildVersionBorders": {
    "from": "2019.1"
  },
  "releaseFilters": [{
    "releaseType": "EAP",
    "from" : 128, 
    "to" : 256
  }]
}""", configs, notExistingConfig = setOf(RELEASE))
  }

  @Test
  fun `test parse no applicable versions`() {
    doTestVersions("""
{
  "majorBuildVersionBorders": {
    "from": "2019.3"
  },
  "releaseFilters": [{
    "releaseType": "RELEASE",
    "from" : 0, 
    "to" : 64
  }]
},
{
  "majorBuildVersionBorders": {
    "from": "2020.1"
  },
  "releaseFilters": [{
    "releaseType": "EAP",
    "from" : 128,
    "to" : 256
  }]
}""", notExistingConfig = BUILD_TYPES)
  }

  @Test
  fun `test parse action without endpoints and configurations`() {
    doTestVersions("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1"
  }
}""", notExistingConfig = BUILD_TYPES, notExistingEndpoints = ENDPOINTS)
  }

  @Test
  fun `test parse action without endpoints`() {
    val configs = hashMapOf(
      EAP to newConfig(arrayListOf(EventLogBucketRange(128, 256)))
    )
    doTestVersions("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1"
  },
  "releaseFilters": [{
    "releaseType": "EAP",
    "from" : 128,
    "to" : 256
  }]
}""", configs, notExistingConfig = setOf(RELEASE), notExistingEndpoints = ENDPOINTS)
  }

  @Test
  fun `test parse action without configurations`() {
    val endpoints = hashMapOf(
      "send" to "https://send/endpoint",
      "dictionary" to "https://dictionary/endpoint/"
    )
    doTestVersions("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1"
  },
  "endpoints": {
    "send": "https://send/endpoint",
    "dictionary": "https://dictionary/endpoint/"
  }
}""", notExistingConfig = BUILD_TYPES, existingEndpoints = endpoints)
  }

  @Test
  fun `test parse config`() {
    val endpoints = hashMapOf(
      "send" to "https://send/endpoint",
      "metadata" to "https://metadata/endpoint/",
      "dictionary" to "https://dictionary/endpoint/"
    )
    val configs = hashMapOf(
      RELEASE to newConfig(arrayListOf(EventLogBucketRange(0, 256)))
    )
    doTest("""
{
  "productCode": "IU",
  "versions": [
    {
      "majorBuildVersionBorders": {
        "from": "2019.2"
      },
      "releaseFilters": [{
        "releaseType": "ALL",
        "from": 0, 
        "to": 256
      }],
      "endpoints": {
        "send": "https://send/endpoint",
        "metadata": "https://metadata/endpoint/",
        "dictionary": "https://dictionary/endpoint/"
      }
    }
  ]
}""", configs, endpoints)
  }

  @Test
  fun `test parse config without product code`() {
    val endpoints = hashMapOf(
      "send" to "https://send/endpoint",
      "metadata" to "https://metadata/endpoint/",
      "dictionary" to "https://dictionary/endpoint/"
    )
    val configs = hashMapOf(
      RELEASE to newConfig(arrayListOf(EventLogBucketRange(0, 256)))
    )
    doTest("""
{
  "productCode": "IU",
  "versions": [{
      "majorBuildVersionBorders": {
        "from": "2019.2"
      },
      "releaseFilters": [{
        "releaseType": "ALL",
        "from": 0, 
        "to": 256
      }],
      "endpoints": {
        "send": "https://send/endpoint",
        "metadata": "https://metadata/endpoint/",
        "dictionary": "https://dictionary/endpoint/"
      }
    }
  ]
}""", configs, endpoints)
  }

  @Test
  fun `test parse empty settings`() {
    doTest("{}", emptyMap(), emptyMap())
  }

  @Test
  fun `test parse settings without versions`() {
    doTest("""
{
  "productCode": "IU"
}""", emptyMap(), emptyMap())
  }

  @Test
  fun `test parse settings with empty versions`() {
    doTest("""
{
  "productCode": "IU",
  "versions": []
}""", emptyMap(), emptyMap())
  }

  @Test
  fun `test parse settings with empty version object`() {
    doTest("""
{
  "productCode": "IU",
  "versions": [{}]
}""", emptyMap(), emptyMap())
  }

  @Test
  fun `test parse duplicate endpoints`() {
    doTestError("""
{
  "productCode": "IU",
  "versions": [
    {
      "majorBuildVersionBorders": {
        "from": "2019.2"
      },
      "releaseFilters": [{
        "releaseType": "ALL",
        "from": 0, 
        "to": 256
      }],
      "endpoints": {
        "send": "https://send/endpoint",
        "send": "https://send/second/endpoint",
        "metadata": "https://metadata/endpoint/",
        "dictionary": "https://dictionary/endpoint/"
      }
    }
  ]
}""")
  }
}
