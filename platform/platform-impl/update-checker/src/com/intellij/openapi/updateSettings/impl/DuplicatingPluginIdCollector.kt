// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.RepositoryHelper
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.marketplace.utils.MarketplaceCustomizationService
import com.intellij.idea.AppMode
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.XCollection
import org.apache.http.client.utils.URIBuilder
import java.util.concurrent.TimeUnit
import kotlin.math.max

private val GROUP = EventLogGroup("duplicating.plugin.ids.in.ide.plugin.repositories", 1)

private val IS_ON_MARKETPLACE_FIELD = EventFields.Boolean("is_on_marketplace")
private val NUMBER_OF_REPOSITORIES_FIELD = RoundingIntEventField("number_of_repositories")
private val NUMBER_OF_DIFFERENT_BASE_URLS_OF_REPOSITORIES_FIELD = RoundingIntEventField("number_of_different_base_urls_of_repositories")
private val FOUND_DUPLICATING_PLUGIN_EVENT = GROUP.registerVarargEvent("found.duplicating.plugin.id",
                                                                       IS_ON_MARKETPLACE_FIELD,
                                                                       EventFields.PluginInfo,
                                                                       NUMBER_OF_REPOSITORIES_FIELD,
                                                                       NUMBER_OF_DIFFERENT_BASE_URLS_OF_REPOSITORIES_FIELD)

private val PLUGIN_UPDATE_NOT_ALLOWED_BY_USER_EVENT = GROUP.registerEvent("checking.plugin.updates.forbidden.by.user")

internal class DuplicatingPluginIdStateCollector : ApplicationUsagesCollector() {

  init {
    if (!AppMode.isMonolith()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun getGroup(): EventLogGroup = GROUP

  override suspend fun getMetricsAsync(): Set<MetricEvent> {
    if (!UpdateSettings.getInstance().isPluginsCheckNeeded) {
      //probably the user doesn't expect repository to be requested without his actions
      return setOf(PLUGIN_UPDATE_NOT_ALLOWED_BY_USER_EVENT.metric())
    }
    val data = service<DuplicationPluginIdCachedValuesService>().getUpToDateDuplicatingPluginsData()
    return data.map {
      val pluginId = it.pluginId
      val pluginInfo = when (it.isAvailableOnMarketplace && pluginId != null) {
        true -> getPluginInfoById(PluginId(pluginId))
        false -> null
      }
      FOUND_DUPLICATING_PLUGIN_EVENT.metric(IS_ON_MARKETPLACE_FIELD.with(it.isAvailableOnMarketplace),
                                            EventFields.PluginInfo.with(pluginInfo),
                                            NUMBER_OF_REPOSITORIES_FIELD.with(it.pluginHostsCount),
                                            NUMBER_OF_DIFFERENT_BASE_URLS_OF_REPOSITORIES_FIELD.with(it.strippedPluginHostsCount))
    }.toSet()
  }
}

private class RoundingIntEventField(override val name: String) : PrimitiveEventField<Int>() {

  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Int) {
    fuData.addData(name, roundNumber(value))
  }

  private fun roundNumber(value: Int): Int {
    if (value < 5) return value
    return StatisticsUtil.roundToPowerOfTwo(value)
  }
}

private val CHECK_INTERVAL_MS =
  TimeUnit.MINUTES.toMillis(java.lang.Long.getLong("ide.stat.duplicating.plugin.ids.check.check.interval.minutes",
                                                   TimeUnit.DAYS.toMinutes(3)))

@State(name = "DuplicatingPluginIdCache",
       storages = [Storage(StoragePathMacros.CACHE_FILE, roamingType = RoamingType.DISABLED, exportable = false)])
@Service(Level.APP)
internal class DuplicationPluginIdCachedValuesService : PersistentStateComponent<DuplicationPluginIdCachedValuesService.State> {
  private var currentState: State = State()


  override fun getState(): State {
    return currentState
  }

  override fun loadState(state: State) {
    currentState = state
  }

  internal fun getUpToDateDuplicatingPluginsData(): List<DuplicatingPluginIdData> {
    val state = currentState
    val timeSinceLastCheck: Long = max(System.currentTimeMillis() - state.lastCheckTimestamp, 0)
    return if (timeSinceLastCheck >= CHECK_INTERVAL_MS) {
      val data = collectDuplicatingPluginDataSafely()
      currentState = State(System.currentTimeMillis(), data)
      data
    }
    else {
      currentState.data
    }
  }

  private fun collectDuplicatingPluginDataSafely(): List<DuplicatingPluginIdData> {
    return runCatching { collectDuplicatingPluginData() }.getOrLogException(thisLogger()) ?: emptyList()
  }

  private fun collectDuplicatingPluginData(): List<DuplicatingPluginIdData> {
    val customHosts = RepositoryHelper.getCustomPluginRepositoryHosts()
    if (customHosts.isEmpty()) {
      return emptyList()
    }

    data class Data(
      var existsOnMarketplace: Boolean = false,
      val hosts: MutableList<String> = mutableListOf(),
      val strippedHosts: MutableSet<String> = mutableSetOf(),
    )

    fun stripHost(host: String): String {
      return URIBuilder(host).removeQuery().build().toString()
    }

    fun cleanupDownloadUrl(downloadUrl: String): String {
      return downloadUrl.trimEnd('/')
    }

    val dataMap = mutableMapOf<PluginId, Data>()
    for (initialHost in customHosts) {
      val host = cleanupDownloadUrl(initialHost)
      val strippedHost = stripHost(host)
      val pluginResult = runCatching { RepositoryHelper.loadPluginModels(host, null, null) }
      val pluginModels = pluginResult.getOrHandleException {
        thisLogger().warn("Fail to get plugins from $host", it)
      } ?: continue
      for (model in pluginModels) {
        val data = dataMap.getOrPut(model.pluginId) { Data() }
        data.hosts.add(host)
        data.strippedHosts.add(strippedHost)
      }
    }

    val marketplaceURL = cleanupDownloadUrl(MarketplaceCustomizationService.getInstance().getPluginDownloadUrl())
    val strippedMarketplaceURL = stripHost(marketplaceURL)
    val marketplacePlugins = MarketplaceRequests.getInstance().getMarketplacePlugins(null)
    for (pluginId in marketplacePlugins) {
      val data = dataMap[pluginId] ?: continue
      data.hosts.add(marketplaceURL)
      data.strippedHosts.add(strippedMarketplaceURL)
      data.existsOnMarketplace = true
    }

    return dataMap.entries.filter { it.value.hosts.size > 1 }.map { (pluginId, data) ->
      //double-check: avoid getting private plugin ids into FUS
      val pluginIdString = if (data.existsOnMarketplace) pluginId.idString else null
      DuplicatingPluginIdData(pluginIdString, data.existsOnMarketplace, data.hosts.size, data.strippedHosts.size)
    }
  }

  internal data class State(
    @Attribute("timestamp") var lastCheckTimestamp: Long = 0,
    @XCollection(propertyElementName = "data", elementName = "plugin") var data: List<DuplicatingPluginIdData> = emptyList(),
  )

  internal data class DuplicatingPluginIdData(
    @Attribute("id") var pluginId: String? = null,
    @Attribute("fromMarket") var isAvailableOnMarketplace: Boolean = false,
    @Attribute("fullHosts") var pluginHostsCount: Int = 0,
    @Attribute("strippedHosts") var strippedPluginHostsCount: Int = 0,
  )
}

