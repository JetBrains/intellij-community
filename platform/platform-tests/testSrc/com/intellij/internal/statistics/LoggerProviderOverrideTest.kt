// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider.Companion.EP_NAME
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LoggerProviderOverrideTest : BasePlatformTestCase() {
  companion object {
    private const val fusRecorderId = "FUS"
  }

  private class TestLoggerProvider: StatisticsEventLoggerProvider(fusRecorderId, 123, DEFAULT_SEND_FREQUENCY_MS, DEFAULT_MAX_FILE_SIZE_BYTES) {
    override fun isRecordEnabled() = false
    override fun isSendEnabled() = false
  }

  override fun setUp() {
    super.setUp()
    installEP()
  }

  fun installEP() {
    val ep = ApplicationManager.getApplication().extensionArea.getExtensionPoint(EP_NAME)
    ep.registerExtension(TestLoggerProvider(), LoadingOrder.FIRST, testRootDisposable)
  }

  fun testFUSLoggerProviderOverridden() {
    val provider = StatisticsEventLogProviderUtil.getEventLogProvidersExt(fusRecorderId).first()
    assertInstanceOf(provider, TestLoggerProvider::class.java)
  }

}