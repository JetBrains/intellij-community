// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import com.intellij.ide.Region
import com.intellij.internal.statistic.eventLog.ExternalEventLogSettings
import com.intellij.internal.statistic.eventLog.StatsAppConnectionSettings
import com.intellij.internal.statistic.eventLog.connection.EventLogUploadSettingsClient
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.fus.reporting.configuration.ConfigurationClientFactory
import com.jetbrains.fus.reporting.configuration.RegionCode
import org.assertj.core.api.Assertions

private const val URL = "https://localhost/"
private const val RECORDER = "FUS"
private const val PRODUCT_CODE = "IC"
private const val PRODUCT_VERSION = "2025.1"
private const val CONFIG_URL = "https://resources.jetbrains.com/storage/fus/config/v4/FUS/IC.json"
private const val CHINA_CONFIG_URL = "https://resources.jetbrains.com.cn/storage/fus/config/v4/FUS/IC.json"
private const val TEST_CONFIG_URL = "https://resources.jetbrains.com/storage/fus/config/v4/test/FUS/IC.json"

class ExternalEventLogSettingsTest : BasePlatformTestCase() {
  private class TestExternalEventLogSettings : ExternalEventLogSettings {
    override fun forceDisableCollectionConsent(): Boolean = true
    override fun forceLoggingAlwaysEnabled(): Boolean = true
    override fun getExtraLogUploadHeaders(): Map<String, String> = emptyMap()
  }

  private val connectionSettings = StatsAppConnectionSettings()
  override fun setUp() {
    super.setUp()
    installEp()
  }

  fun installEp() {
    ExtensionTestUtil.maskExtensions(ExternalEventLogSettings.EP_NAME, listOf(TestExternalEventLogSettings()), testRootDisposable)
  }

  fun testSubstitution() {
    val configurationClient = ConfigurationClientFactory.create(RECORDER,
                                                                PRODUCT_CODE,
                                                                PRODUCT_VERSION,
                                                                false,
                                                                connectionSettings,
                                                                RegionCode.ALL,
                                                                1)
    Assertions.assertThat(configurationClient.configurationUrl).isNotEqualTo(URL)
  }

  /**
   * Check that the config url is https://resources.jetbrains.com/storage/fus/config/v4/FUS/IC.json for other regions.
   */
  fun testUsualRegionConfigurationUrl() {
    val configurationClient = ConfigurationClientFactory.Companion.create(
      RECORDER,
      PRODUCT_CODE,
      PRODUCT_VERSION,
      false,
      connectionSettings,
      RegionCode.ALL,
      1
    )
    Assertions.assertThat(configurationClient.configurationUrl).isEqualTo(CONFIG_URL)
  }

  /**
   * Check that the config url is https://resources.jetbrains.com.cn/storage/fus/config/v4/FUS/IC.json for the China region.
   */
  fun testChinaRegionConfigurationUrl() {
    val configurationClient = ConfigurationClientFactory.Companion.create(
      RECORDER,
      PRODUCT_CODE,
      PRODUCT_VERSION,
      false,
      connectionSettings,
      RegionCode.CN,
      1
    )
    Assertions.assertThat(configurationClient.configurationUrl).isEqualTo(CHINA_CONFIG_URL)
  }

  /**
   * Check that the config url is https://resources.jetbrains.com.cn/test/storage/fus/config/v4/FUS/IC.json for test.
   */
  fun testTestEnvironmentConfigurationUrl() {
    val configurationClient = ConfigurationClientFactory.Companion.create(
      RECORDER,
      PRODUCT_CODE,
      PRODUCT_VERSION,
      true,
      connectionSettings,
      RegionCode.ALL,
      1
    )
    Assertions.assertThat(configurationClient.configurationUrl).isEqualTo(TEST_CONFIG_URL)
  }

  /**
   * Check that the external name of com.intellij.ide.CHINA is equal "china"
   */
  fun testChinaRegion() {
    Assertions.assertThat(EventLogUploadSettingsClient.chinaRegion).isEqualTo(Region.CHINA.externalName())
  }

  fun testForceDisableCollectionConsent() {
    assertFalse(StatisticsUploadAssistant.isCollectAllowed())
  }

  fun testForceCollectionWithoutRecord() {
    assertTrue(!StatisticsUploadAssistant.isCollectAllowed() && StatisticsUploadAssistant.isCollectAllowedOrForced())
  }
}