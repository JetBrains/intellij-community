// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.envTest.upload

import com.intellij.internal.statistic.config.SerializationHelper
import com.intellij.internal.statistic.envTest.ApacheContainer
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.fus.reporting.model.config.v4.Configuration
import com.jetbrains.fus.reporting.model.config.v4.ConfigurationMajorBuildVersionBorder
import com.jetbrains.fus.reporting.model.config.v4.ConfigurationReleaseFilter
import com.jetbrains.fus.reporting.model.config.v4.ConfigurationVersion
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
    val majorVersionBorders = ConfigurationMajorBuildVersionBorder(productVersion)

    val endpoints = hashMapOf<String, String>()
    if (sendPath != null) {
      endpoints["send"] = getSendUrl()
    }
    endpoints["metadata"] = container.getBaseUrl("$TEST_SERVER_ROOT/$metadataPath").toString()

    val filter = ConfigurationReleaseFilter("ALL", fromBucket, toBucket)

    val version = ConfigurationVersion(majorVersionBorders, endpoints, options, listOf(filter))

    val externalSettings = Configuration(PRODUCT_CODE, listOf(version))

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
