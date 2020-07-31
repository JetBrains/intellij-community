// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.envTest.upload

import com.intellij.internal.statistic.envTest.ApacheContainer
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Paths

class EventLogConfigBuilder(private val container: ApacheContainer, private val tmpLocalRoot: String) {
  private val TEST_SERVER_ROOT = "upload"

  private var productVersion: String = PRODUCT_VERSION
  private var customSendHost: String? = null
  private var sendPath: String? = "dump-request.php"
  private var whitelistPath: String = ""
  private var fromBucket: Int = 0
  private var toBucket: Int = 256

  fun withSendUrlPath(path: String?): EventLogConfigBuilder {
    sendPath = path
    return this
  }

  fun withSendHost(host: String): EventLogConfigBuilder {
    customSendHost = host
    return this
  }

  fun withWhitelistUrlPath(path: String): EventLogConfigBuilder {
    whitelistPath = path
    return this
  }

  fun withToBucket(to: Int): EventLogConfigBuilder {
    toBucket = to
    return this
  }

  fun withProductVersion(version: String): EventLogConfigBuilder {
    productVersion = version
    return this
  }

  fun create() {
    val sendUrl = sendPath?.let { """"send": "${getSendUrl()}",""" } ?: ""
    val whitelistUrl = container.getBaseUrl("$TEST_SERVER_ROOT/$whitelistPath").toString()
    val config = """
{
  "productCode": "${PRODUCT_CODE}",
  "versions": [
    {
      "majorBuildVersionBorders": {
        "from": "${productVersion}"
      },
      "releaseFilters": [
        {
          "releaseType": "ALL",
          "from": $fromBucket,
          "to": $toBucket
        }
      ],
      "endpoints": {
        $sendUrl
        "whitelist": "$whitelistUrl"
      }
    }
  ]
}
    """.trimIndent()
    val path = String.format(SETTINGS_ROOT, RECORDER_ID, PRODUCT_CODE)
    val file = Paths.get(tmpLocalRoot).resolve(path).toFile()
    FileUtil.writeToFile(file, config)
  }

  private fun getSendUrl(): String {
    if (customSendHost != null) {
      return "$customSendHost/$TEST_SERVER_ROOT/$sendPath"
    }
    return container.getBaseUrl("$TEST_SERVER_ROOT/$sendPath").toString()
  }
}
