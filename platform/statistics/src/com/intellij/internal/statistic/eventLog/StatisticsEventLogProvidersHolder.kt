// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtils
import com.intellij.idea.AppMode
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider.Companion.EP_NAME
import com.intellij.internal.statistic.utils.PluginType
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.APP)
internal class StatisticsEventLogProvidersHolder(coroutineScope: CoroutineScope) {
  // Small temporary inconsistency between `eventLoggerProviders` and `eventLoggerProvidersExt` doesn't really matter,
  // and it will be smaller than other white noise in data.
  private val eventLoggerProviders: AtomicReference<Map<String, StatisticsEventLoggerProvider>> =
    AtomicReference(calculateEventLogProvider())
  private val eventLoggerProvidersExt: AtomicReference<Map<String, Collection<StatisticsEventLoggerProvider>>> =
    AtomicReference(calculateEventLogProviderExt())

  init {
    if (ApplicationManager.getApplication().extensionArea.hasExtensionPoint(EP_NAME)) {
      EP_NAME.addChangeListener(coroutineScope) { eventLoggerProviders.set(calculateEventLogProvider()) }
      EP_NAME.addChangeListener(coroutineScope) { eventLoggerProvidersExt.set(calculateEventLogProviderExt()) }
    }
  }

  fun getEventLogProvider(recorderId: String): StatisticsEventLoggerProvider =
    eventLoggerProviders.get()[recorderId] ?: EmptyStatisticsEventLoggerProvider(recorderId)

  fun getEventLogProviders(): Collection<StatisticsEventLoggerProvider> =
    eventLoggerProviders.get().values

  fun getEventLogProvidersExt(recorderId: String): Collection<StatisticsEventLoggerProvider> =
    eventLoggerProvidersExt.get()[recorderId] ?: listOf(EmptyStatisticsEventLoggerProvider(recorderId))

  private fun calculateEventLogProvider(): Map<String, StatisticsEventLoggerProvider> {
    return calculateEventLogProviderExt().mapValues {
      it.value.find { provider ->
        if (PluginManagerCore.isRunningFromSources() || AppMode.isRunningFromDevBuild()) true
        else PluginUtils.getPluginDescriptorOrPlatformByClassName(provider::class.java.name)
          ?.let { plugin -> PluginManagerCore.isDevelopedExclusivelyByJetBrains(plugin) }
          ?: false
      } ?: EmptyStatisticsEventLoggerProvider(it.key)
    }
  }

  private fun calculateEventLogProviderExt(): Map<String, Collection<StatisticsEventLoggerProvider>> =
    getAllEventLogProviders().groupBy { it.recorderId }


  private fun getAllEventLogProviders(): Sequence<StatisticsEventLoggerProvider> {
    val providers = EP_NAME.extensionsIfPointIsRegistered
    if (providers.isEmpty()) {
      return emptySequence()
    }
    val isJetBrainsProduct = isJetBrainsProduct()
    return providers.asSequence()
      .filter { isProviderApplicable(isJetBrainsProduct, it.recorderId, it) }
  }

  private fun isJetBrainsProduct(): Boolean =
    ApplicationInfo.getInstance()?.shortCompanyName.isNullOrEmpty() || PlatformUtils.isJetBrainsProduct()

  private fun isProviderApplicable(isJetBrainsProduct: Boolean, recorderId: String, extension: StatisticsEventLoggerProvider): Boolean {
    if (recorderId == extension.recorderId) {
      if (!isJetBrainsProduct || !StatisticsRecorderUtil.isBuildInRecorder(recorderId)) {
        return true
      }
      val pluginInfo = getPluginInfo(extension::class.java)
      return if (recorderId == "MLSE" || recorderId == "ML") {
        pluginInfo.isDevelopedByJetBrains()
      }
      else {
        pluginInfo.type == PluginType.PLATFORM || pluginInfo.type == PluginType.FROM_SOURCES || pluginInfo.isAllowedToInjectIntoFUS()
      }
    }
    return false
  }
}
