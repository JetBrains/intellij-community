// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider.Companion.EP_NAME
import com.intellij.internal.statistic.utils.PluginType
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.util.PlatformUtils
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.APP)
internal class StatisticsEventLogProvidersHolder {
  private val eventLoggerProviders: AtomicReference<Map<String, StatisticsEventLoggerProvider>> =
    AtomicReference(calculateEventLogProvider())

  init {
    if (ApplicationManager.getApplication().extensionArea.hasExtensionPoint(EP_NAME)) {
      EP_NAME.addChangeListener(Runnable { eventLoggerProviders.set(calculateEventLogProvider()) }, null)
    }
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

  private fun getAllEventLogProviders(): Sequence<StatisticsEventLoggerProvider> {
    val providers = EP_NAME.extensionsIfPointIsRegistered
    if (providers.isEmpty()) {
      return emptySequence()
    }
    val isJetBrainsProduct = isJetBrainsProduct()
    return providers.asSequence()
      .filter { isProviderApplicable(isJetBrainsProduct, it.recorderId, it) }
      .distinctBy { it.recorderId }
  }

  private fun isJetBrainsProduct(): Boolean {
    val appInfo = ApplicationInfo.getInstance()
    return if (appInfo == null || appInfo.shortCompanyName.isNullOrEmpty()) true else PlatformUtils.isJetBrainsProduct()
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