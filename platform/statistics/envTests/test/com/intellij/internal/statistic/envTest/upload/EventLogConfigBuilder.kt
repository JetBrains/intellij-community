// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.envTest.upload

import com.intellij.internal.statistic.envTest.ApacheContainer
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Paths

internal const val SETTINGS_FILE = "settings.xml"

class EventLogConfigBuilder(private val container: ApacheContainer, private val tmpLocalRoot: String) {
  private val TEST_SERVER_ROOT = "upload"

  private var customSendHost: String? = null
  private var sendPath: String = "dump-request.php"
  private var whitelistPath: String = ""
  private var permitted: Int = 100

  fun withSendUrlPath(path: String): EventLogConfigBuilder {
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

  fun withPermittedTraffic(percent: Int): EventLogConfigBuilder {
    permitted = percent
    return this
  }

  fun create() {
    val sendUrl = customSendHost?.let { "$customSendHost/$TEST_SERVER_ROOT/$sendPath" }
                  ?: container.getBaseUrl("$TEST_SERVER_ROOT/$sendPath").toString()

    val whitelistUrl = container.getBaseUrl("$TEST_SERVER_ROOT/$whitelistPath").toString()
    val settings = """<service url="$sendUrl" percent-traffic="$permitted" white-list-service="$whitelistUrl"/>"""
    val file = Paths.get(tmpLocalRoot).resolve(SETTINGS_FILE).toFile()
    FileUtil.writeToFile(file, settings)
  }
}
