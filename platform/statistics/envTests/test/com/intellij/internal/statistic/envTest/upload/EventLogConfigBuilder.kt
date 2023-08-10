// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.envTest.upload

import com.intellij.internal.statistic.config.EventLogExternalSettings
import com.intellij.internal.statistic.config.bean.EventLogConfigVersions
import com.intellij.internal.statistic.config.bean.EventLogMajorVersionBorders
import com.intellij.internal.statistic.envTest.ApacheContainer
import com.intellij.internal.statistic.config.SerializationHelper
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Paths

class EventLogConfigBuilder(private val container: ApacheContainer, private val tmpLocalRoot: String) {
  private val TEST_SERVER_ROOT = "upload"

  private var productVersion: String = PRODUCT_VERSION
  private var customSendHost: String? = null
  private var sendPath: String? = "dump-request.php"
  private var metadataPath: String = ""
  private var fromBucket: Int = 0
  private var toBucket: Int = 256
  private var options = hashMapOf<String, String>()

  fun withSendUrlPath(path: String?): EventLogConfigBuilder {
    sendPath = path
    return this
  }

  fun withSendHost(host: String): EventLogConfigBuilder {
    customSendHost = host
    return this
  }

  fun withMetadataUrlPath(path: String): EventLogConfigBuilder {
    metadataPath = path
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

  fun withOption(key: String, value: String): EventLogConfigBuilder {
    options[key] = value
    return this
  }

  fun create() {
    val externalSettings = EventLogExternalSettings()
    externalSettings.productCode = PRODUCT_CODE
    val version = EventLogConfigVersions()
    val majorVersionBorders = EventLogMajorVersionBorders()
    majorVersionBorders.from = productVersion
    version.majorBuildVersionBorders = majorVersionBorders
    val filter = EventLogConfigVersions.EventLogConfigFilterCondition()
    filter.releaseType = "ALL"
    filter.from = fromBucket
    filter.to = toBucket
    version.releaseFilters = listOf(filter)
    val endpoints = hashMapOf<String, String>()
    if (sendPath != null) {
      endpoints["send"] = getSendUrl()
    }
    endpoints["metadata"] = container.getBaseUrl("$TEST_SERVER_ROOT/$metadataPath").toString()
    version.endpoints = endpoints
    version.options = options
    externalSettings.versions = listOf(version)

    val config = SerializationHelper.serializeToSingleLine(externalSettings)
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
