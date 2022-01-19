// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.config

import com.intellij.internal.statistic.config.bean.EventLogBucketRange
import com.intellij.internal.statistic.config.bean.EventLogSendConfiguration
import com.intellij.internal.statistic.config.eventLog.EventLogBuildType
import com.intellij.internal.statistic.config.eventLog.EventLogBuildType.EAP
import com.intellij.internal.statistic.config.eventLog.EventLogBuildType.RELEASE
import org.junit.Test

class EventLogConfigVersionParserTest : EventLogConfigBaseParserTest() {

  private fun doTestNoVersion(versions: String) {
    val config = """
{
  "productCode": "IU",
  "versions": [""" + versions + """]
}"""
    doTest(config, notExistingEndpoints = setOf("send", "metadata"))
  }

  private fun doTestVersion(versions: String, expected: Map<String, String>,
                            expectedConfig: Map<EventLogBuildType, EventLogSendConfiguration>? = null) {
    val buildConfig = expectedConfig ?: mapOf(
      EAP to EventLogSendConfiguration(listOf(EventLogBucketRange(0, 256))),
      RELEASE to EventLogSendConfiguration(listOf(EventLogBucketRange(0, 256)))
    )

    val config = """
{
  "productCode": "IU",
  "versions": [""" + versions + """]
}"""
    doTest(config, existingConfig = buildConfig, existingEndpoints = expected)
  }

  //region Endpoints with invalid versions
  @Test
  fun `test parse endpoint with no versions`() {
    doTestNoVersion("""
{
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }

  @Test
  fun `test parse endpoint with empty version`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }

  @Test
  fun `test parse endpoint with unknown numerical property in version`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
    "unknown": 2019
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }

  @Test
  fun `test parse endpoint with unknown string property in version`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
    "unknown": "2019.1"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }

  @Test
  fun `test parse endpoint with from and unknown string property in version`() {
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1",
    "unknown": "2019.2"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""", mapOf("send" to "https://send/endpoint", "metadata" to "https://metadata/endpoint/"))
  }

  @Test
  fun `test parse endpoint with to and unknown string property in version`() {
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "unknown": "2019.3",
    "to": "2020.1"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""", mapOf("send" to "https://send/endpoint", "metadata" to "https://metadata/endpoint/"))
  }

  @Test
  fun `test parse endpoint with from and to and unknown string property in version`() {
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "unknown": "2019.3",
    "from": "2019.1",
    "to": "2020.1"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""", mapOf("send" to "https://send/endpoint", "metadata" to "https://metadata/endpoint/"))
  }

  @Test
  fun `test parse endpoint with from bigger than to in version`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2019.2",
    "to": "2019.1"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }

  @Test
  fun `test parse endpoint with major to bigger than to in version`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2020.1",
    "to": "2019.1"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }

  @Test
  fun `test parse endpoint with boolean from version`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
    "from": true
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }

  @Test
  fun `test parse endpoint with alphabet from version`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
    "from": "my.version"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }
  //endregion

  //region Endpoints with [from, ...) version
  @Test
  fun `test parse endpoint with numerical from version`() {
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "from": 2019
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""", mapOf("send" to "https://send/endpoint", "metadata" to "https://metadata/endpoint/"))
  }

  @Test
  fun `test parse endpoint with only from and version equal to from`() {
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2019.2"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""", mapOf("send" to "https://send/endpoint", "metadata" to "https://metadata/endpoint/"))
  }

  @Test
  fun `test parse endpoint with only from and version bigger than from`() {
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""", mapOf("send" to "https://send/endpoint", "metadata" to "https://metadata/endpoint/"))
  }

  @Test
  fun `test parse endpoint with only from and major version bigger than from`() {
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2018.2"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""", mapOf("send" to "https://send/endpoint", "metadata" to "https://metadata/endpoint/"))
  }

  @Test
  fun `test parse endpoint with only from and version smaller than from`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2019.3"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }

  @Test
  fun `test parse endpoint with only from and major version smaller than from`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2020.2"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }
  //endregion

  //region Endpoints with [.., to) version
  @Test
  fun `test parse endpoint with only to and version equal to from`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
    "to": "2019.2"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }

  @Test
  fun `test parse endpoint with only to and version bigger than from`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
    "to": "2019.1"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }

  @Test
  fun `test parse endpoint with only to and major version bigger than from`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
    "to": "2018.2"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }

  @Test
  fun `test parse endpoint with only to and version smaller than from`() {
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "to": "2019.3"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""", mapOf("send" to "https://send/endpoint", "metadata" to "https://metadata/endpoint/"))
  }

  @Test
  fun `test parse endpoint with only to and major version smaller than from`() {
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "to": "2020.1"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""", mapOf("send" to "https://send/endpoint", "metadata" to "https://metadata/endpoint/"))
  }
  //endregion

  //region Endpoints with [from, to) version
  @Test
  fun `test parse simple endpoints from config`() {
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1",
    "to": "2019.3"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""", mapOf("send" to "https://send/endpoint", "metadata" to "https://metadata/endpoint/"))
  }

  @Test
  fun `test parse endpoint with version equal to from and smaller than to`() {
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2019.2",
    "to": "2019.3"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""", mapOf("send" to "https://send/endpoint", "metadata" to "https://metadata/endpoint/"))
  }

  @Test
  fun `test parse endpoint with version equal to from and to`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2019.2",
    "to": "2019.2"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }

  @Test
  fun `test parse endpoint with version bigger than from and smaller than to`() {
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1",
    "to": "2019.3"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""", mapOf("send" to "https://send/endpoint", "metadata" to "https://metadata/endpoint/"))
  }

  @Test
  fun `test parse endpoint with version bigger than from and bigger than to`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2018.2",
    "to": "2019.1"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }

  @Test
  fun `test parse endpoint with major version bigger than from and smaller than to`() {
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2018.3",
    "to": "2020.1"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""", mapOf("send" to "https://send/endpoint", "metadata" to "https://metadata/endpoint/"))
  }

  @Test
  fun `test parse endpoint with major version bigger than from and bigger than to`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2018.2",
    "to": "2019.1"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }

  @Test
  fun `test parse endpoint with version smaller than from and to`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2019.3",
    "to": "2020.1"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }

  @Test
  fun `test parse endpoint with major version smaller than from and to`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2018.1",
    "to": "2018.3"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
}""")
  }
  //endregion

  //region Endpoints with multiple versions
  @Test
  fun `test parse endpoint with first version applicable`() {
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2019.2",
    "to": "2019.3"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
},
{
  "majorBuildVersionBorders": {
    "from": "2019.2",
    "to": "2019.3"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 32, "to": 64 }],
  "endpoints": {
    "send": "https://send/endpoint-last",
    "metadata": "https://metadata/endpoint-last"
  }
}""", mapOf("send" to "https://send/endpoint", "metadata" to "https://metadata/endpoint/"))
  }

  @Test
  fun `test parse endpoint with last version applicable`() {
    val config = mapOf(
      RELEASE to EventLogSendConfiguration(listOf(EventLogBucketRange(32, 64)))
    )
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2017.1",
    "to": "2018.3"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
},
{
  "majorBuildVersionBorders": {
    "from": "2019.2"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 32, "to": 64 }],
  "endpoints": {
    "send": "https://send/endpoint-last",
    "metadata": "https://metadata/endpoint-last"
  }
}""", mapOf("send" to "https://send/endpoint-last", "metadata" to "https://metadata/endpoint-last"), config)
  }

  @Test
  fun `test parse endpoint with middle version applicable`() {
    val config = mapOf(
      RELEASE to EventLogSendConfiguration(listOf(EventLogBucketRange(32, 64)))
    )
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2017.1",
    "to": "2018.3"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
},
{
  "majorBuildVersionBorders": {
    "from": "2019.1",
    "to": "2019.3"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 32, "to": 64 }],
  "endpoints": {
    "send": "https://send/endpoint-middle",
    "metadata": "https://metadata/endpoint-middle"
  }
},
{
  "majorBuildVersionBorders": {
    "from": "2019.3"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 32, "to": 64 }],
  "endpoints": {
    "send": "https://send/endpoint-last",
    "metadata": "https://metadata/endpoint-last"
  }
}""", mapOf("send" to "https://send/endpoint-middle", "metadata" to "https://metadata/endpoint-middle"), config)
  }

  @Test
  fun `test parse endpoint with no versions applicable`() {
    doTestNoVersion("""
{
  "majorBuildVersionBorders": {
    "to": "2018.3"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
},
{
  "majorBuildVersionBorders": {
    "from": "2019.3"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 32, "to": 64 }],
  "endpoints": {
    "send": "https://send/endpoint-last",
    "metadata": "https://metadata/endpoint-last"
  }
}""")
  }

  @Test
  fun `test parse endpoint with two versions applicable`() {
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2019.1",
    "to": "2019.3"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
},
{
  "majorBuildVersionBorders": {
    "from": "2019.2"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 32, "to": 64 }],
  "endpoints": {
    "send": "https://send/endpoint-last",
    "metadata": "https://metadata/endpoint-last"
  }
}""", mapOf("send" to "https://send/endpoint", "metadata" to "https://metadata/endpoint/"))
  }

  @Test
  fun `test parse endpoint with two last versions applicable`() {
    val config = mapOf(
      RELEASE to EventLogSendConfiguration(listOf(EventLogBucketRange(32, 64)))
    )
    doTestVersion("""
{
  "majorBuildVersionBorders": {
    "from": "2018.1",
    "to": "2019.1"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 0, "to": 256 }],
  "endpoints": {
    "send": "https://send/endpoint",
    "metadata": "https://metadata/endpoint/"
  }
},
{
  "majorBuildVersionBorders": {
    "from": "2019.1"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 32, "to": 64 }],
  "endpoints": {
    "send": "https://send/endpoint-middle",
    "metadata": "https://metadata/endpoint-middle"
  }
},
{
  "majorBuildVersionBorders": {
    "from": "2019.2"
  },
  "releaseFilters": [{ "releaseType": "ALL", "from": 32, "to": 64 }],
  "endpoints": {
    "send": "https://send/endpoint-last",
    "metadata": "https://metadata/endpoint-last"
  }
}""", mapOf("send" to "https://send/endpoint-middle", "metadata" to "https://metadata/endpoint-middle"), config)
  }
  //endregion
}
