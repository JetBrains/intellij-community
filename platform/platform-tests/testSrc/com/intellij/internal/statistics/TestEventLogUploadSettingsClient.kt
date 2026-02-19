// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.EventLogInternalApplicationInfo
import com.intellij.internal.statistic.eventLog.connection.EventLogSettingsClient
import com.intellij.internal.statistic.eventLog.connection.metadata.StatsBasicConnectionSettings
import com.intellij.internal.statistic.eventLog.connection.metadata.StatsConnectionSettings
import com.intellij.internal.statistic.eventLog.validator.storage.FusComponentProvider
import com.jetbrains.fus.reporting.configuration.ConfigurationClientFactory
import com.jetbrains.fus.reporting.jvm.JvmHttpClient
import com.jetbrains.fus.reporting.jvm.ProxyInfo
import java.security.SecureRandom
import java.time.Duration
import javax.net.ssl.SSLContext

const val DEFAULT_RECORDER_ID = "FUS"

internal class TestEventLogUploadSettingsClient(configurationUrl: String) : EventLogSettingsClient() {
  override val applicationInfo = TestEventLogApplicationInfo()
  override val configurationClient = ConfigurationClientFactory.createTest(
    applicationInfo.productCode,
    applicationInfo.productVersion,
    httpClient = JvmHttpClient(
      proxyProvider = { configurationUrl ->
        ProxyInfo(applicationInfo.connectionSettings.provideProxy(configurationUrl).proxy)
      },
      sslContextProvider = { applicationInfo.connectionSettings.provideSSLContext() },
      extraHeadersProvider = { applicationInfo.connectionSettings.provideExtraHeaders() },
      userAgent = applicationInfo.connectionSettings.provideUserAgent(),
      timeout = Duration.ofSeconds(1)
    ),
    configurationUrl = configurationUrl,
    serializer = FusComponentProvider.FusJacksonSerializer()
  )
  override val recorderId: String = DEFAULT_RECORDER_ID
}

internal class TestEventLogApplicationInfo() : EventLogInternalApplicationInfo(true, true) {
  companion object {
    const val DEFAULT_PRODUCT_VERSION = "2019.2"
    const val DEFAULT_PRODUCT_CODE = "IU"
  }

  override fun getProductVersion(): String = DEFAULT_PRODUCT_VERSION
  override fun getProductCode(): String = DEFAULT_PRODUCT_CODE
  override fun getConnectionSettings(): StatsConnectionSettings {
    return StatsBasicConnectionSettings(
      "IntelliJ IDEA/243.SNAPSHOT",
      emptyMap(),
      SSLContext.getInstance("TLS").apply {
        init(null, null, SecureRandom())
      })
  }
}