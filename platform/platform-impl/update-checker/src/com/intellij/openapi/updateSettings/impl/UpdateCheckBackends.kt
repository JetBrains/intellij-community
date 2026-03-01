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
import com.intellij.openapi.updateSettings.impl.PluginDownloader.compareVersionsSkipBrokenAndIncompatible
import com.intellij.openapi.updateSettings.impl.UpdateChecker.allowedDowngrade
import com.intellij.openapi.updateSettings.impl.UpdateChecker.allowedUpgrade
import com.intellij.openapi.updateSettings.impl.UpdateChecker.checkAndPrepareToInstall
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.HttpRequests
import com.intellij.util.text.VersionComparatorUtil
import tools.jackson.databind.DatabindException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal class PreparedPluginUpdates(
  val toUpdate: Map<PluginId, PluginDownloader>,
  val toUpdateDisabled: Map<PluginId, PluginDownloader>,
  val models: Map<PluginId, PluginUiModel>,
)

internal abstract class RemotePluginRepository(val id: String) {
  abstract fun findUpdates(
    buildNumber: BuildNumber?,
    state: InstalledPluginsState,
    plugins: Collection<PluginId>,
    indicator: ProgressIndicator?,
  ): PreparedPluginUpdates
}

internal open class MarketplacePluginRepository : RemotePluginRepository("default-host") {
  override fun findUpdates(
    buildNumber: BuildNumber?,
    state: InstalledPluginsState,
    plugins: Collection<PluginId>,
    indicator: ProgressIndicator?,
  ): PreparedPluginUpdates {
    val marketplacePluginIds = MarketplaceRequests.getInstance().getMarketplacePlugins(indicator)
    val idsToUpdate = plugins.filter { it in marketplacePluginIds }.toSet()
    val updates = findUpdates(idsToUpdate, buildNumber)

    val toUpdate = mutableMapOf<PluginId, PluginDownloader>()
    val toUpdateDisabled = mutableMapOf<PluginId, PluginDownloader>()
    val models = HashMap<PluginId, PluginUiModel>()

    val installedDescriptors = getAllInstalledPlugins(state)
    for (id in plugins) {
      val lastUpdate = updates.find { it.pluginId == id.idString }
      val descriptor = installedDescriptors[id]

      if (lastUpdate != null &&
          (descriptor == null || compareVersionsSkipBrokenAndIncompatible(lastUpdate.version, descriptor, buildNumber) > 0)) {
        runCatching { MarketplaceRequests.loadPluginModel(id.idString, lastUpdate, indicator) }
          .onFailure {
            if (!isNetworkError(it)) throw it

            thisLogger().warn("Unable to read update metadata for plugin: $id, ${it::class.java} ${it.message}")
          }
          .onSuccess {
            it.externalPluginIdForScreenShots = lastUpdate.externalPluginId
            models[id] = it
          }
          .onSuccess { prepareDownloader(state, it, buildNumber, toUpdate, toUpdateDisabled, indicator, null) }
      }
    }

    return PreparedPluginUpdates(toUpdate, toUpdateDisabled, models)
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
internal class MarketplaceUpdateCheckPluginRepository : MarketplacePluginRepository() {
  override fun findUpdates(idsToUpdate: Set<PluginId>, buildNumber: BuildNumber?): List<IdeCompatibleUpdate> {
    return MarketplaceRequests.checkInstalledPluginUpdate(idsToUpdate, buildNumber, true)
  }
}

internal class CustomPluginRepository(private val host: String) : RemotePluginRepository(host) {
  override fun findUpdates(
    buildNumber: BuildNumber?,
    state: InstalledPluginsState,
    plugins: Collection<PluginId>,
    indicator: ProgressIndicator?,
  ): PreparedPluginUpdates {
    val toUpdate = mutableMapOf<PluginId, PluginDownloader>()
    val toUpdateDisabled = mutableMapOf<PluginId, PluginDownloader>()
    val models = HashMap<PluginId, PluginUiModel>()

    RepositoryHelper.loadPluginModels(host, buildNumber, indicator).forEach { model ->
      val id = model.pluginId
      if (plugins.contains(id)) {
        prepareDownloader(state, model, buildNumber, toUpdate, toUpdateDisabled, indicator, host)
      }
      // collect latest plugin models from custom repos
      val storedDescriptor = models[id]
      if (storedDescriptor == null
          || (VersionComparatorUtil.compare(model.version, storedDescriptor.version) > 0
              && allowedUpgrade(storedDescriptor.getDescriptor(), model.getDescriptor()))
          || (VersionComparatorUtil.compare(model.version, storedDescriptor.version) < 0
              && allowedDowngrade(storedDescriptor.getDescriptor(), model.getDescriptor()))) {
        models[id] = model
      }
    }

    return PreparedPluginUpdates(toUpdate, toUpdateDisabled, models)
  }
}

@RequiresBackgroundThread
private fun prepareDownloader(
  state: InstalledPluginsState,
  descriptor: PluginUiModel,
  buildNumber: BuildNumber?,
  toUpdate: MutableMap<PluginId, PluginDownloader>,
  toUpdateDisabled: MutableMap<PluginId, PluginDownloader>,
  indicator: ProgressIndicator?,
  host: String?,
) {
  val downloader = PluginDownloader.createDownloader(descriptor, host, buildNumber)
  state.onDescriptorDownload(descriptor)
  checkAndPrepareToInstall(downloader, state,
                           if (UpdateCheckerPluginsFacade.getInstance().isDisabled(downloader.id)) toUpdateDisabled else toUpdate,
                           buildNumber, indicator)
}

private fun isNetworkError(it: Throwable): Boolean {
  return it is SocketTimeoutException
         || it is UnknownHostException
         || it is HttpRequests.HttpStatusException && it.statusCode == HttpURLConnection.HTTP_NOT_FOUND
         || it is DatabindException && it.message?.contains("end-of-input") == true
}
