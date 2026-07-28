// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.ide.plugins.RepositoryHelper
import com.intellij.ide.plugins.marketplace.IdeCompatibleUpdate
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.updateSettings.impl.CategorizedDownloaders.PluginStatus.Disabled
import com.intellij.openapi.updateSettings.impl.CategorizedDownloaders.PluginStatus.Enabled
import com.intellij.openapi.updateSettings.impl.PluginUpdateCandidateDecision.AcceptUpdateToHigherVersion
import com.intellij.openapi.updateSettings.impl.PluginUpdateCandidateDecision.AcceptUpdateToLowerVersionForBrokenOrIncompatiblePlugin
import com.intellij.openapi.updateSettings.impl.PluginUpdateCandidateDecision.KeepExistingPlugin
import com.intellij.openapi.updateSettings.impl.UpdateChecker.allowedUpgrade
import com.intellij.openapi.updateSettings.impl.UpdateChecker.determineUpdateWithDownloaderDecision
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.HttpRequests
import com.intellij.util.text.VersionComparatorUtil
import tools.jackson.databind.DatabindException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal class PreparedPluginUpdates(
  val categorizedDownloaders: CategorizedDownloaders,
  val models: Map<PluginId, PluginUiModel>,
) {
  fun getAllPluginIds(): Set<PluginId> {
    return categorizedDownloaders.getAllPluginIds() + models.keys
  }

  fun filterByPluginIds(pluginIds: Collection<PluginId>): PreparedPluginUpdates {
    return PreparedPluginUpdates(
      categorizedDownloaders.filterByPluginIds(pluginIds),
      models.filterKeys { it in pluginIds }
    )
  }
}

internal class CategorizedDownloaders {
  private val categories = ArrayList<MutableMap<PluginId, PluginDownloader>>()

  private enum class PluginStatus { Enabled, Disabled }
  private enum class UpdateType { HigherVersion, LowerVersion }

  private fun getIndex(type: UpdateType, status: PluginStatus): Int = type.ordinal * PluginStatus.entries.size + status.ordinal

  init {
    forAll { _, _ -> categories.add(mutableMapOf()) }
  }

  private fun forAll(action: (UpdateType, PluginStatus) -> Unit) {
    PluginStatus.entries.forEach { status -> UpdateType.entries.forEach { type -> action(type, status) } }
  }

  private fun category(type: UpdateType, status: PluginStatus): MutableMap<PluginId, PluginDownloader> = categories[getIndex(type, status)]

  fun getAllPluginIds(): Set<PluginId> {
    return buildSet {
      forAll { type, status -> addAll(category(type, status).keys) }
    }
  }

  fun allPluginsUpdatingToHigherVersion(): Set<PluginId> {
    return buildSet { PluginStatus.entries.forEach { status -> addAll(category(UpdateType.HigherVersion, status).keys) } }
  }

  fun getDownloadersForAllEnabledPlugins(): Collection<PluginDownloader> {
    return buildSet { UpdateType.entries.forEach { type -> addAll(category(type, Enabled).values) } }
  }

  fun getDownloadersForAllDisabledPlugins(): Collection<PluginDownloader> {
    return buildSet { UpdateType.entries.forEach { type -> addAll(category(type, Disabled).values) } }
  }

  fun filterByPluginIds(pluginIds: Collection<PluginId>): CategorizedDownloaders {
    val updates = CategorizedDownloaders()
    forAll { type, status ->
      val filtered = category(type, status).filterKeys { it in pluginIds }
      updates.category(type, status).putAll(filtered)
    }
    return updates
  }

  fun addDownloader(downloader: PluginDownloader, check: PluginUpdateCandidateDecision) {
    val type = when (check) {
      AcceptUpdateToHigherVersion -> UpdateType.HigherVersion
      AcceptUpdateToLowerVersionForBrokenOrIncompatiblePlugin -> UpdateType.LowerVersion
      KeepExistingPlugin -> return
    }
    val status = if (UpdateCheckerPluginsFacade.getInstance().isDisabled(downloader.id)) {
      Disabled
    }
    else {
      Enabled
    }
    category(type, status)[downloader.id] = downloader
  }

  fun putAll(updates: CategorizedDownloaders) {
    forAll { type, status -> category(type, status).putAll(updates.category(type, status)) }
  }

  fun removeAllUpdatesWithLowerVersion(updatePluginId: PluginId) {
    return PluginStatus.entries.forEach { status -> category(UpdateType.LowerVersion, status).remove(updatePluginId) }
  }
}

internal abstract class RemotePluginRepository(val id: String) {
  abstract fun findUpdates(
    buildNumber: BuildNumber?,
    state: InstalledPluginsState,
    plugins: Collection<PluginId>,
    indicator: ProgressIndicator?,
  ): PreparedPluginUpdates
}

internal open class MarketplaceLikePluginRepository : RemotePluginRepository("default-host") {
  override fun findUpdates(
    buildNumber: BuildNumber?,
    state: InstalledPluginsState,
    plugins: Collection<PluginId>,
    indicator: ProgressIndicator?,
  ): PreparedPluginUpdates {
    val marketplacePluginIds = MarketplaceRequests.getInstance().getMarketplacePlugins(indicator)
    val idsToUpdate = plugins.filter { it in marketplacePluginIds }.toSet()
    val updates = findUpdates(idsToUpdate, buildNumber)

    val categorizedDownloaders = CategorizedDownloaders()
    val models = HashMap<PluginId, PluginUiModel>()

    val installedDescriptors = getAllInstalledPlugins(state)
    for (id in plugins) {
      val lastUpdate = updates.find { it.pluginId == id.idString }
      val descriptor = installedDescriptors[id]

      if (lastUpdate == null) continue
      if (descriptor == null ||
          PluginUpdateVersionChecker.determinePluginUpdateDecision(lastUpdate.version, descriptor, buildNumber) != KeepExistingPlugin) {
        runCatching { MarketplaceRequests.loadPluginModel(id.idString, lastUpdate, indicator) }
          .onFailure {
            if (!isNetworkError(it)) throw it

            thisLogger().warn("Unable to read update metadata for plugin: $id, ${it::class.java} ${it.message}")
          }
          .onSuccess {
            it.externalPluginIdForScreenShots = lastUpdate.externalPluginId
            models[id] = it
          }
          .onSuccess { prepareDownloader(state, it, buildNumber, categorizedDownloaders, null) }
      }
    }

    return PreparedPluginUpdates(categorizedDownloaders, models)
  }

  private fun getAllInstalledPlugins(state: InstalledPluginsState): Map<PluginId, IdeaPluginDescriptor> {
    val ids = state.installedPlugins + UpdateCheckerPluginsFacade.getInstance().getInstalledPlugins()
    return ids.associateBy { it.pluginId }
  }

  protected open fun findUpdates(idsToUpdate: Set<PluginId>, buildNumber: BuildNumber?): List<IdeCompatibleUpdate> {
    return MarketplaceRequests.loadLastCompatiblePluginUpdate(idsToUpdate, buildNumber, true)
  }
}

/**
 * Special backend for checking updates of plugins that passes additional analytics ID.
 */
internal class MarketplaceUpdateCheckPluginRepository : MarketplaceLikePluginRepository() {
  override fun findUpdates(idsToUpdate: Set<PluginId>, buildNumber: BuildNumber?): List<IdeCompatibleUpdate> {
    return MarketplaceRequests.checkInstalledPluginUpdate(idsToUpdate, buildNumber, true)
  }
}

internal fun getMatchingPluginUpdateSource(backend: RemotePluginRepository): PluginUpdateSourceId {
  return when (backend) {
    is MarketplaceLikePluginRepository -> PluginUpdateSourceService.getInstance().createMarketplacePluginUpdateSourceId()
    is CustomPluginRepository -> PluginUpdateSourceService.getInstance().createCustomRepositoryPluginUpdateSourceId(backend.host)
    else -> throw IllegalStateException("Unexpected plugin repository type: $backend")
  }
}

internal class CustomPluginRepository(internal val host: String) : RemotePluginRepository(host) {
  override fun findUpdates(
    buildNumber: BuildNumber?,
    state: InstalledPluginsState,
    plugins: Collection<PluginId>,
    indicator: ProgressIndicator?,
  ): PreparedPluginUpdates {
    val categorizedDownloaders = CategorizedDownloaders()
    val models = HashMap<PluginId, PluginUiModel>()

    RepositoryHelper.loadPluginModels(host, buildNumber, indicator).forEach { model ->
      val id = model.pluginId
      if (plugins.contains(id)) {
        prepareDownloader(state, model, buildNumber, categorizedDownloaders, host)
      }
      // collect latest plugin models from custom repos
      val storedDescriptor = models[id]
      if (storedDescriptor == null
          || (VersionComparatorUtil.compare(model.version, storedDescriptor.version) > 0
              && allowedUpgrade(storedDescriptor.getDescriptor(), model.getDescriptor()))) {
        models[id] = model
      }
    }

    return PreparedPluginUpdates(categorizedDownloaders, models)
  }
}

@RequiresBackgroundThread
private fun prepareDownloader(
  state: InstalledPluginsState,
  descriptor: PluginUiModel,
  buildNumber: BuildNumber?,
  categorizedDownloaders: CategorizedDownloaders,
  host: String?,
) {
  val downloader = PluginDownloader.createDownloader(descriptor, host, buildNumber)
  state.onDescriptorDownload(descriptor)
  val check = determineUpdateWithDownloaderDecision(downloader, state, buildNumber)
  categorizedDownloaders.addDownloader(downloader, check)
}

private fun isNetworkError(it: Throwable): Boolean {
  return it is SocketTimeoutException
         || it is UnknownHostException
         || it is HttpRequests.HttpStatusException && it.statusCode == HttpURLConnection.HTTP_NOT_FOUND
         || it is DatabindException && it.message?.contains("end-of-input") == true
}
