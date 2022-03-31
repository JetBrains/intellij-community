// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider.Companion.EP_NAME
import com.intellij.internal.statistic.utils.PluginType
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.APP)
class StatisticsEventLogProvidersHolder {
  private val eventLoggerProviders: AtomicReference<Map<String, StatisticsEventLoggerProvider>> =
    AtomicReference(calculateEventLogProvider())

  init {
    EP_NAME.addChangeListener(Runnable { eventLoggerProviders.set(calculateEventLogProvider()) }, null)
  }

  fun getEventLogProvider(recorderId: String): StatisticsEventLoggerProvider {
    return eventLoggerProviders.get()[recorderId] ?: EmptyStatisticsEventLoggerProvider(recorderId)
  }

  fun getEventLogProviders(): Collection<StatisticsEventLoggerProvider> {
    return eventLoggerProviders.get().values
  }

  private fun calculateEventLogProvider(): Map<String, StatisticsEventLoggerProvider> {
    return getAllEventLogProviders().associateBy { it.recorderId }
  }

  private fun getAllEventLogProviders(): List<StatisticsEventLoggerProvider> {
    val providers = EP_NAME.extensionsIfPointIsRegistered
    if (providers.isEmpty()) {
      return emptyList()
    }
    val isJetBrainsProduct = isJetBrainsProduct()
    return ContainerUtil.filter(providers) { isProviderApplicable(isJetBrainsProduct, it.recorderId, it) }.distinctBy { it.recorderId }
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
      }
      else {
        pluginInfo.type == PluginType.PLATFORM || pluginInfo.type == PluginType.FROM_SOURCES || pluginInfo.isAllowedToInjectIntoFUS()
      }
    }
    return false
  }
}