// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.application.ApplicationManager

object StatisticsEventLogProviderUtil {
  @JvmStatic
  fun getEventLogProviders(): List<StatisticsEventLoggerProvider> {
    return ApplicationManager.getApplication().getService(StatisticsEventLogProvidersHolder::class.java).getEventLogProviders().toList()
  }

  @JvmStatic
  fun getEventLogProvider(recorderId: String): StatisticsEventLoggerProvider {
    return ApplicationManager.getApplication().getService(StatisticsEventLogProvidersHolder::class.java).getEventLogProvider(recorderId)
  }

  @JvmStatic
  fun getExternalEventLogSettings(): ExternalEventLogSettings? {
    if (ApplicationManager.getApplication().extensionArea.hasExtensionPoint(ExternalEventLogSettings.EP_NAME)) {
      val externalEventLogSettings = ExternalEventLogSettings.EP_NAME.findFirstSafe { settings: ExternalEventLogSettings ->
        getPluginInfo(settings.javaClass).isAllowedToInjectIntoFUS()
      }
      return externalEventLogSettings
    }
    return null
  }

  @JvmStatic
  fun forceLoggingAlwaysEnabled(): Boolean {
    if (ApplicationManager.getApplication().extensionArea.hasExtensionPoint(ExternalEventLogSettings.EP_NAME)) {
      val externalEventLogSettings = ExternalEventLogSettings.EP_NAME.findFirstSafe { settings: ExternalEventLogSettings ->
        getPluginInfo(settings.javaClass).isAllowedToInjectIntoFUS()
      }
      if (externalEventLogSettings != null && externalEventLogSettings.forceLoggingAlwaysEnabled()) {
        return true
      }
    }

    if (ApplicationManager.getApplication().extensionArea.hasExtensionPoint(ExternalEventLogListenerProviderExtension.EP_NAME)) {
      for (logListenerProvider in ExternalEventLogListenerProviderExtension.EP_NAME.extensionList) {
        if (getPluginInfo(logListenerProvider.javaClass).isAllowedToInjectIntoFUS() &&
            logListenerProvider.forceLoggingAlwaysEnabled()) {
          return true
        }
      }
    }

    return false
  }
}