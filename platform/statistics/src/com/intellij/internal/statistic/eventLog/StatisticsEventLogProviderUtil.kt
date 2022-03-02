// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider.Companion.EP_NAME
import com.intellij.internal.statistic.utils.PluginType
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.containers.ContainerUtil

object StatisticsEventLogProviderUtil {
  private val LOG = Logger.getInstance(StatisticsEventLogProviderUtil::class.java)

  @JvmStatic
  fun getEventLogProviders(): List<StatisticsEventLoggerProvider> {
    val providers = EP_NAME.extensionsIfPointIsRegistered
    if (providers.isEmpty()) {
      return emptyList()
    }
    val isJetBrainsProduct = isJetBrainsProduct()
    return ContainerUtil.filter(providers) { isProviderApplicable(isJetBrainsProduct, it.recorderId, it) }.distinctBy { it.recorderId }
  }

  @JvmStatic
  fun getEventLogProvider(recorderId: String): StatisticsEventLoggerProvider {
    if (ApplicationManager.getApplication().extensionArea.hasExtensionPoint(EP_NAME.name)) {
      val isJetBrainsProduct = isJetBrainsProduct()
      val provider = EP_NAME.findFirstSafe { isProviderApplicable(isJetBrainsProduct, recorderId, it) }
      provider?.let {
        if (LOG.isTraceEnabled) {
          LOG.trace("Use event log provider '${provider.javaClass.simpleName}' for recorder-id=${recorderId}")
        }
        return it
      }
    }
    LOG.warn("Cannot find event log provider with recorder-id=${recorderId}")
    return EmptyStatisticsEventLoggerProvider(recorderId)
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

  private fun isJetBrainsProduct(): Boolean {
    val appInfo = ApplicationInfo.getInstance()
    if (appInfo == null || StringUtil.isEmpty(appInfo.shortCompanyName)) {
      return true
    }
    return PlatformUtils.isJetBrainsProduct()
  }

  private fun isProviderApplicable(isJetBrainsProduct: Boolean, recorderId: String, extension: StatisticsEventLoggerProvider): Boolean {
    if (recorderId == extension.recorderId) {
      if (!isJetBrainsProduct || !StatisticsRecorderUtil.isBuildInRecorder(recorderId)) {
        return true
      }
      val pluginInfo = getPluginInfo(extension::class.java)

      return if (recorderId == "MLSE") {
        pluginInfo.isDevelopedByJetBrains()
      } else {
        pluginInfo.type == PluginType.PLATFORM || pluginInfo.type == PluginType.FROM_SOURCES || pluginInfo.isAllowedToInjectIntoFUS()
      }
    }
    return false
  }
}