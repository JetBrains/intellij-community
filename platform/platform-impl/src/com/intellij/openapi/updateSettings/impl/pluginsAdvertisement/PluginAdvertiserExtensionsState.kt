// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeFactory
import com.intellij.openapi.fileTypes.PlainTextLikeFileType
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import java.util.*
import java.util.concurrent.TimeUnit

data class PluginAdvertiserExtensionsData(
  // Either extension or file name. Depends on which of the two properties has more priority for advertising plugins for this specific file.
  val extensionOrFileName: String,
  val plugins: Set<PluginsAdvertiser.Plugin>
)

@State(name = "PluginAdvertiserExtensions", storages = [Storage("pluginAdvertiser.xml")])
class PluginAdvertiserExtensionsStateService : PersistentStateComponent<PluginAdvertiserExtensionsStateService.State> {
  companion object {
    @JvmStatic
    fun getInstance(): PluginAdvertiserExtensionsStateService = service()
  }

  class State {
    var localState = mutableMapOf<String, PluginsAdvertiser.Plugin>()
  }

  private var state = State()

  internal val cache: Cache<String, Optional<PluginAdvertiserExtensionsData>> =
    CacheBuilder
      .newBuilder()
      .expireAfterWrite(1, TimeUnit.HOURS)
      .build()

  override fun getState(): State = state

  override fun loadState(state: State) {
    this.state = state
  }

  fun registerLocalPlugin(extensionOrFileName: String, plugin: PluginDescriptor) {
    state.localState[extensionOrFileName] = PluginsAdvertiser.Plugin(plugin.pluginId.idString, plugin.name, plugin.isBundled, false)
  }

  fun getLocalPlugin(fileName: String, fullExtension: String?): PluginAdvertiserExtensionsData? {
    state.localState[fileName]?.let { plugin ->
      return PluginAdvertiserExtensionsData(fileName, setOf(plugin))
    }
    fullExtension?.let { state.localState[it] }?.let { plugin ->
      return PluginAdvertiserExtensionsData(fullExtension, setOf(plugin))
    }
    return null
  }
}

class PluginAdvertiserExtensionsState(private val project: Project, private val service: PluginAdvertiserExtensionsStateService) {

  companion object {
    private val LOG = Logger.getInstance(PluginsAdvertiser::class.java)

    @JvmStatic
    fun getInstance(project: Project): PluginAdvertiserExtensionsState =
      PluginAdvertiserExtensionsState(project, PluginAdvertiserExtensionsStateService.getInstance())
  }

  fun ignoreExtensionOrFileNameAndInvalidateCache(extensionOrFileName: String) {
    UnknownFeaturesCollector.getInstance(project).ignoreFeature(createUnknownExtensionFeature(extensionOrFileName))
    invalidateCacheDataForKey(extensionOrFileName)

  }

  private val enabledExtensionOrFileNames = ContainerUtil.newConcurrentSet<String>()

  fun addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName: String) {
    enabledExtensionOrFileNames += extensionOrFileName
    invalidateCacheDataForKey(extensionOrFileName)
  }

  private fun getCachedData(extensionOrFileName: String): PluginAdvertiserExtensionsData? =
    service.cache.getIfPresent(extensionOrFileName)?.orElse(null)

  fun updateCache(extensionOrFileName: String): Boolean {
    LOG.assertTrue(!ApplicationManager.getApplication().isReadAccessAllowed)
    LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread)

    if (service.cache.getIfPresent(extensionOrFileName) != null) {
      return false
    }
    val knownExtensions = PluginsAdvertiser.loadExtensions()
    if (knownExtensions == null) {
      return false
    }

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

  private fun requestData(
    extensionOrFileName: String,
    knownExtensions: PluginsAdvertiser.KnownExtensions
  ): PluginAdvertiserExtensionsData? {
    val allPlugins = knownExtensions.find(extensionOrFileName)?.toSet()
    if (allPlugins == null || allPlugins.isEmpty()) {
      LOG.debug("No features for extension $extensionOrFileName")
      return null
    }
    val pluginIdsFromMarketplace = MarketplaceRequests.getInstance()
      .getLastCompatiblePluginUpdate(allPlugins.map { it.myPluginId }, null).map { it.pluginId }.toSet()
    val compatiblePlugins = allPlugins.filter {
      it.myFromCustomRepository
      || it.myBundled
      || pluginIdsFromMarketplace.contains(it.myPluginId)
    }.toSet()
    if (compatiblePlugins.isNotEmpty()) {
      LOG.debug(String.format(
        "Found following plugins for '%s': [%s]", extensionOrFileName,
        compatiblePlugins.map { it.myPluginId }.joinToString { it })
      )
      return PluginAdvertiserExtensionsData(extensionOrFileName, compatiblePlugins)
    }
    LOG.debug("No plugins for extension $extensionOrFileName")
    return null
  }

  private fun isIgnored(extensionOrFileName: String): Boolean =
    enabledExtensionOrFileNames.contains(extensionOrFileName)
    || UnknownFeaturesCollector.getInstance(project).isIgnored(createUnknownExtensionFeature(extensionOrFileName))

  private fun createUnknownExtensionFeature(extensionOrFileName: String): UnknownFeature? =
    UnknownFeature(FileTypeFactory.FILE_TYPE_FACTORY_EP.name, "File Type", extensionOrFileName, extensionOrFileName)
}