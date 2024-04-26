// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.EventLogInternalApplicationInfo
import com.intellij.internal.statistic.eventLog.ExternalEventLogSettings
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions

private const val URL = "https://localhost/"

class ExternalEventLogSettingsTest : BasePlatformTestCase() {
  private class TestExternalEventLogSettings: ExternalEventLogSettings {
    override fun forceDisableCollectionConsent(): Boolean = true
    override fun forceLoggingAlwaysEnabled(): Boolean = true
    override fun getExtraLogUploadHeaders(): Map<String, String> = emptyMap()
  }

  override fun setUp() {
    super.setUp()
    installEp()
  }

  fun installEp() {
    ExtensionTestUtil.maskExtensions(ExternalEventLogSettings.EP_NAME, listOf(TestExternalEventLogSettings()), testRootDisposable)
  }

  fun testSubstitution() {
    val applicationInfo = EventLogInternalApplicationInfo(false, true)
    Assertions.assertThat(applicationInfo.templateUrl).isNotEqualTo(URL)
  }

  fun testForceDisableCollectionConsent() {
    assertFalse(StatisticsUploadAssistant.isCollectAllowed())
  }

  fun testForceCollectionWithoutRecord() {
    assertTrue(!StatisticsUploadAssistant.isCollectAllowed() && StatisticsUploadAssistant.isCollectAllowedOrForced())
  }
}