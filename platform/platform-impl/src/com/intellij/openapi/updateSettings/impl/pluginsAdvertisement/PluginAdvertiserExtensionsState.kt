// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeFactory
import com.intellij.openapi.fileTypes.PlainTextLikeFileType
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal data class PluginAdvertiserExtensionsData(
  // Either extension or file name. Depends on which of the two properties has more priority for advertising plugins for this specific file.
  val extensionOrFileName: String,
  val plugins: Set<PluginsAdvertiser.Plugin>
)

@State(name = "PluginAdvertiserExtensions", storages = [Storage(StoragePathMacros.CACHE_FILE)])
@Service(Service.Level.APP)
internal class PluginAdvertiserExtensionsStateService : SimpleModificationTracker(), PersistentStateComponent<PluginAdvertiserExtensionsStateService.State> {
  companion object {
    @JvmStatic
    fun getInstance(): PluginAdvertiserExtensionsStateService = service()
  }

  class State {
    val localState = LinkedHashMap<String, PluginsAdvertiser.Plugin>()
  }

  private var state = State()

  internal val cache = Caffeine
    .newBuilder()
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build<String, Optional<PluginAdvertiserExtensionsData>>()

  override fun getState(): State = state

  override fun loadState(state: State) {
    this.state = state
  }

  fun registerLocalPlugin(extensionOrFileName: String, plugin: PluginDescriptor) {
    state.localState.put(extensionOrFileName, PluginsAdvertiser.Plugin(plugin.pluginId.idString, plugin.name, plugin.isBundled, false))
    // no need to waste time to check that map is really changed - registerLocalPlugin is not called often after start-up,
    // so, mod count will be not incremented too often
    incModificationCount()
  }

  fun getLocalPlugin(fileName: String, fullExtension: String?): PluginAdvertiserExtensionsData? {
    state.localState.get(fileName)?.let { plugin ->
      return PluginAdvertiserExtensionsData(fileName, setOf(plugin))
    }
    fullExtension?.let { state.localState.get(it) }?.let { plugin ->
      return PluginAdvertiserExtensionsData(fullExtension, setOf(plugin))
    }
    return null
  }
}

internal class PluginAdvertiserExtensionsState(private val project: Project, private val service: PluginAdvertiserExtensionsStateService) {
  companion object {
    private val LOG = logger<PluginsAdvertiser>()

    @JvmStatic
    fun getInstance(project: Project): PluginAdvertiserExtensionsState {
      return PluginAdvertiserExtensionsState(project, PluginAdvertiserExtensionsStateService.getInstance())
    }
  }

  fun ignoreExtensionOrFileNameAndInvalidateCache(extensionOrFileName: String) {
    UnknownFeaturesCollector.getInstance(project).ignoreFeature(createUnknownExtensionFeature(extensionOrFileName))
    invalidateCacheDataForKey(extensionOrFileName)

  }

  private val enabledExtensionOrFileNames: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

  fun addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName: String) {
    enabledExtensionOrFileNames.add(extensionOrFileName)
    invalidateCacheDataForKey(extensionOrFileName)
  }

  private fun getCachedData(extensionOrFileName: String): PluginAdvertiserExtensionsData? {
    return service.cache.getIfPresent(extensionOrFileName)?.orElse(null)
  }

  fun updateCache(extensionOrFileName: String): Boolean {
    LOG.assertTrue(!ApplicationManager.getApplication().isReadAccessAllowed)
    LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread)

    if (service.cache.getIfPresent(extensionOrFileName) != null) {
      return false
    }
    val knownExtensions = PluginsAdvertiser.loadExtensions() ?: return false
    val newData = requestData(extensionOrFileName, knownExtensions)
    service.cache.put(extensionOrFileName, Optional.ofNullable(newData))
    return true
  }

  private fun invalidateCacheDataForKey(extensionOrFileName: String) {
    service.cache.invalidate(extensionOrFileName)
  }

  fun requestExtensionData(
    fileName: String,
    fileType: FileType,
    fullExtension: String?
  ): PluginAdvertiserExtensionsData? {
    val alreadySupported = fileType !is PlainTextLikeFileType

    if (fullExtension != null && isIgnored(fullExtension)) {
      LOG.debug(String.format("Extension '%s' is ignored in project '%s'", fullExtension, project.name))
      return null
    }
    if (isIgnored(fileName)) {
      LOG.debug(String.format("File '%s' is ignored in project '%s'", fileName, project.name))
      return null
    }

    if (fullExtension == null && fileType is FakeFileType) {
      return null
    }

    service.getLocalPlugin(fileName, fullExtension)?.let { return it }

    val knownExtensions = PluginsAdvertiser.loadExtensions()
    if (knownExtensions != null) {
      //if file is already supported by IDE then only plugins exactly matching fileName should be suggested
      if (alreadySupported) {
        val availablePlugins = knownExtensions.find(fileName)
        if (availablePlugins != null) {
          val plugins = availablePlugins.map { it.myPluginId }.toSet()
          for (loadedPlugin in PluginManagerCore.getLoadedPlugins()) {
            if (loadedPlugin.isEnabled && plugins.contains(loadedPlugin.pluginId.idString)) {
              LOG.debug(String.format(
                "File '%s' (type: '%s') is already supported by fileName via '%s'(id: '%s') plugin",
                fileName,
                fileType,
                loadedPlugin.name,
                loadedPlugin.pluginId.idString
              ))
              return null
            }
          }
        }
        LOG.debug(String.format(
          "File '%s' (type: '%s') is already supported therefore looking only for plugins exactly matching fileName",
          fileName,
          fileType
        ))
        return getCachedData(fileName)
      }
      return fullExtension?.let { getCachedData(it) } ?: getCachedData(fileName)
    }
    LOG.debug("No known extensions loaded")
    return null
  }

  private fun requestData(extensionOrFileName: String,
                          knownExtensions: PluginsAdvertiser.KnownExtensions): PluginAdvertiserExtensionsData? {
    val allPlugins = knownExtensions.find(extensionOrFileName)?.toHashSet()
    if (allPlugins == null || allPlugins.isEmpty()) {
      LOG.debug("No features for extension $extensionOrFileName")
      return null
    }

    val pluginIdsFromMarketplace = MarketplaceRequests.getInstance()
      .getLastCompatiblePluginUpdate(allPlugins.map { it.myPluginId }, null).map { it.pluginId }.toSet()
    val compatiblePlugins = allPlugins
      .asSequence()
      .filter {
      it.myFromCustomRepository
      || it.myBundled
      || pluginIdsFromMarketplace.contains(it.myPluginId)
    }.toHashSet()
    if (compatiblePlugins.isEmpty()) {
      LOG.debug("No plugins for extension $extensionOrFileName")
      return null
    }

    LOG.debug {
      "Found following plugins for '${extensionOrFileName}': ${compatiblePlugins.map { it.myPluginId }.joinToString { it }}"
    }
    return PluginAdvertiserExtensionsData(extensionOrFileName, compatiblePlugins)
  }

  private fun isIgnored(extensionOrFileName: String): Boolean {
    return (enabledExtensionOrFileNames.contains(extensionOrFileName)
            || UnknownFeaturesCollector.getInstance(project).isIgnored(createUnknownExtensionFeature(extensionOrFileName)))
  }

  private fun createUnknownExtensionFeature(extensionOrFileName: String): UnknownFeature {
    return UnknownFeature(FileTypeFactory.FILE_TYPE_FACTORY_EP.name, "File Type", extensionOrFileName, extensionOrFileName)
  }
}