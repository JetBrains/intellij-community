// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName

object StatisticsEventLogProviderUtil {
  private val LOG = Logger.getInstance(StatisticsEventLogger::class.java)
  private val EP_NAME = ExtensionPointName<StatisticsEventLoggerProvider>("com.intellij.statistic.eventLog.eventLoggerProvider")

  @JvmStatic
  fun getEventLogProviders(): List<StatisticsEventLoggerProvider> {
    return EP_NAME.extensionsIfPointIsRegistered
  }

  @JvmStatic
  fun getEventLogProvider(recorderId: String): StatisticsEventLoggerProvider {
    if (ApplicationManager.getApplication().extensionArea.hasExtensionPoint(EP_NAME.name)) {
      EP_NAME.findFirstSafe { it.recorderId == recorderId }?.let { return it }
    }
    LOG.warn("Cannot find event log provider with recorder-id=${recorderId}")
    return EmptyStatisticsEventLoggerProvider(recorderId)
  }
}