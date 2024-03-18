// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.ExternalEventLogListenerProviderExtension
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ExternalEventLogListenerProviderExtensionTest : BasePlatformTestCase() {
  private class TestEventLogListenerProvider : ExternalEventLogListenerProviderExtension {
    override fun forceLoggingAlwaysEnabled(): Boolean = true
  }

  fun testForceCollectionWithoutRecord() {
    ExtensionTestUtil.maskExtensions(ExternalEventLogListenerProviderExtension.EP_NAME, listOf(TestEventLogListenerProvider()),
                                     testRootDisposable)

    assertTrue(!StatisticsUploadAssistant.isCollectAllowed() && StatisticsUploadAssistant.isCollectAllowedOrForced())
  }

  fun testNotForceCollectionWithoutRecord() {
    assertFalse(!StatisticsUploadAssistant.isCollectAllowed() && StatisticsUploadAssistant.isCollectAllowedOrForced())
  }
}