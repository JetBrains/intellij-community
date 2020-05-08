// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextLikeFileType
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import java.util.*
import java.util.concurrent.TimeUnit

data class PluginAdvertiserExtensionsKey(
  val fileName: String,
  val fileTypeName: String, // Do not use [FileType] here because it might prevent plugin dynamic unloading.
  val extension: String?
)

data class PluginAdvertiserExtensionsData(
  // Either extension or file name. Depends on which of the two properties has more priority for advertising plugins for this specific file.
  val extensionOrFileName: String,
  val plugins: Set<PluginsAdvertiser.Plugin>
)

class PluginAdvertiserExtensionsState(private val project: Project) {

  companion object {
    private val LOG = Logger.getInstance(PluginAdvertiserExtensionsState::class.java)

    @JvmStatic
    fun getInstance(project: Project): PluginAdvertiserExtensionsState = project.service()
  }

  private val cache: Cache<PluginAdvertiserExtensionsKey, Optional<PluginAdvertiserExtensionsData>> =
    CacheBuilder
      .newBuilder()
      .expireAfterWrite(5, TimeUnit.MINUTES)
      .build()

  private val enabledExtensionOrFileNames = ContainerUtil.newConcurrentSet<String>()

  fun addEnabledExtensionOrFileName(extensionOrFileName: String) {
    enabledExtensionOrFileNames += extensionOrFileName
  }

  fun getCachedData(extensionsKey: PluginAdvertiserExtensionsKey): PluginAdvertiserExtensionsData? =
    cache.getIfPresent(extensionsKey)?.orElse(null)

  fun updateCache(extensionsKey: PluginAdvertiserExtensionsKey) {
    LOG.assertTrue(!ApplicationManager.getApplication().isReadAccessAllowed)
    LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread)

    if (cache.getIfPresent(extensionsKey) != null) {
      return
    }
    val newData = requestExtensionData(extensionsKey)
    cache.put(extensionsKey, Optional.ofNullable(newData))
  }

  fun invalidateCacheForKey(extensionsKey: PluginAdvertiserExtensionsKey) {
    cache.invalidate(extensionsKey)
  }

  private fun requestExtensionData(key: PluginAdvertiserExtensionsKey): PluginAdvertiserExtensionsData? {
    val fileName = key.fileName
    val fileType = FileTypeManager.getInstance().findFileTypeByName(key.fileTypeName) ?: return null
    val extension = key.extension
    val alreadySupported = fileType !is PlainTextLikeFileType

    val fullExtension = extension?.let { "*.$it" }
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
        return requestData(fileName, knownExtensions)
      }
      return fullExtension?.let { requestData(it, knownExtensions) } ?: requestData(fileName, knownExtensions)
    }
    LOG.debug("No known extensions loaded")
    return null
  }

  private fun requestData(
    extensionOrFileName: String,
    knownExtensions: PluginsAdvertiser.KnownExtensions
  ): PluginAdvertiserExtensionsData? {
    val allPlugins = knownExtensions.find(extensionOrFileName)?.toSet()
    if (allPlugins == null || allPlugins.isEmpty()) return null
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