// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "ReplaceGetOrSet")

package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.advertiser.PluginFeatureCacheService
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileTypes.FileNameMatcher
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeFactory
import com.intellij.openapi.fileTypes.PlainTextLikeFileType
import com.intellij.openapi.fileTypes.impl.DetectedByContentFileType
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.Strings
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.intellij.util.containers.mapSmartSet
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class PluginAdvertiserExtensionsData(
  // Either extension or file name. Depends on which of the two properties has more priority for advertising plugins for this specific file.
  val extensionOrFileName: String,
  val plugins: Set<PluginData> = emptySet(),
)

@State(name = "PluginAdvertiserExtensions", storages = [Storage(StoragePathMacros.CACHE_FILE)])
@Service(Service.Level.APP)
class PluginAdvertiserExtensionsStateService : SerializablePersistentStateComponent<PluginAdvertiserExtensionsStateService.State>(
  State()
) {
  private val tracker = SimpleModificationTracker()

  @Serializable
  data class State(val plugins: MutableMap<String, PluginData> = HashMap())

  override fun getStateModificationCount() = tracker.modificationCount

  companion object {
    private val LOG = logger<PluginAdvertiserExtensionsStateService>()

    @JvmStatic
    val instance
      get() = service<PluginAdvertiserExtensionsStateService>()

    @JvmStatic
    fun getFullExtension(fileName: String): String? = Strings.toLowerCase(
      FileUtilRt.getExtension(fileName)).takeIf { it.isNotEmpty() }?.let { "*.$it" }

    @RequiresBackgroundThread
    @RequiresReadLockAbsence
    private fun requestCompatiblePlugins(
      extensionOrFileName: String,
      dataSet: Set<PluginData>,
    ): Set<PluginData> {
      if (dataSet.isEmpty()) {
        LOG.debug("No features for extension $extensionOrFileName")
        return emptySet()
      }

      val pluginIdsFromMarketplace = MarketplaceRequests
        .getLastCompatiblePluginUpdate(dataSet.mapSmartSet { it.pluginId })
        .map { it.pluginId }
        .toSet()

      val plugins = dataSet
        .asSequence()
        .filter {
          it.isFromCustomRepository
          || it.isBundled
          || pluginIdsFromMarketplace.contains(it.pluginIdString)
        }.toSet()

      LOG.debug {
        if (plugins.isEmpty())
          "No plugins for extension $extensionOrFileName"
        else
          "Found following plugins for '${extensionOrFileName}': ${plugins.joinToString { it.pluginIdString }}"
      }

      return plugins
    }

    @Suppress("HardCodedStringLiteral")
    private fun createUnknownExtensionFeature(extensionOrFileName: String) = UnknownFeature(
      FileTypeFactory.FILE_TYPE_FACTORY_EP.name,
      "File Type",
      extensionOrFileName,
      extensionOrFileName,
    )

    private fun findEnabledPlugin(plugins: Set<String>): IdeaPluginDescriptor? {
      return if (plugins.isNotEmpty())
        PluginManagerCore.getLoadedPlugins().find {
          it.isEnabled && plugins.contains(it.pluginId.idString)
        }
      else
        null
    }
  }

  private val cache = Caffeine
    .newBuilder()
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build<String, PluginAdvertiserExtensionsData>()

  fun createExtensionDataProvider(project: Project) = ExtensionDataProvider(project)

  fun registerLocalPlugin(matcher: FileNameMatcher, descriptor: PluginDescriptor) {
    state.plugins.put(matcher.presentableString, PluginData(descriptor))

    // no need to waste time to check that map is really changed - registerLocalPlugin is not called often after start-up,
    // so, mod count will be not incremented too often
    tracker.incModificationCount()
  }

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  fun updateCache(extensionOrFileName: String): Boolean {
    if (cache.getIfPresent(extensionOrFileName) != null) {
      return false
    }

    val knownExtensions = PluginFeatureCacheService.getInstance().extensions
    if (knownExtensions == null) {
      LOG.debug("No known extensions loaded")
      return false
    }

    val compatiblePlugins = requestCompatiblePlugins(extensionOrFileName, knownExtensions.get(extensionOrFileName))
    updateCache(extensionOrFileName, compatiblePlugins)
    return true
  }

  @VisibleForTesting
  fun updateCache(extensionOrFileName: String, compatiblePlugins: Set<PluginData>) {
    cache.put(extensionOrFileName, PluginAdvertiserExtensionsData(extensionOrFileName, compatiblePlugins))
  }

  inner class ExtensionDataProvider(private val project: Project) {

    private val unknownFeaturesCollector get() = UnknownFeaturesCollector.getInstance(project)
    private val enabledExtensionOrFileNames = Collections.newSetFromMap<String>(ConcurrentHashMap())

    fun ignoreExtensionOrFileNameAndInvalidateCache(extensionOrFileName: String) {
      unknownFeaturesCollector.ignoreFeature(createUnknownExtensionFeature(extensionOrFileName))
      cache.invalidate(extensionOrFileName)
    }

    fun addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName: String) {
      enabledExtensionOrFileNames.add(extensionOrFileName)
      cache.invalidate(extensionOrFileName)
    }

    private fun getByFilenameOrExt(fileNameOrExtension: String): PluginAdvertiserExtensionsData? {
      return state.plugins[fileNameOrExtension]?.let {
        PluginAdvertiserExtensionsData(fileNameOrExtension, setOf(it))
      }
    }

    fun requestExtensionData(fileName: String, fileType: FileType): PluginAdvertiserExtensionsData? {
      val fullExtension = getFullExtension(fileName)
      if (fullExtension != null && isIgnored(fullExtension)) {
        LOG.debug("Extension '$fullExtension' is ignored in project '${project.name}'")
        return null
      }
      if (isIgnored(fileName)) {
        LOG.debug("File '$fileName' is ignored in project '${project.name}'")
        return null
      }

      if (fullExtension == null && fileType is FakeFileType) {
        return null
      }

      // Check if there's a plugin matching the exact file name

      getByFilenameOrExt(fileName)?.let {
        return it
      }

      val knownExtensions = PluginFeatureCacheService.getInstance().extensions
      if (knownExtensions == null) {
        LOG.debug("No known extensions loaded")
        return null
      }

      val plugin = findEnabledPlugin(knownExtensions.get(fileName).mapTo(HashSet()) { it.pluginIdString })
      if (plugin != null) {
        // Plugin supporting the exact file name is installed and enabled, no advertiser is needed
        return null
      }

      val pluginsForExactFileName = cache.getIfPresent(fileName)
      if (pluginsForExactFileName != null && pluginsForExactFileName.plugins.isNotEmpty()) {
        return pluginsForExactFileName
      }
      if (knownExtensions.get(fileName).isNotEmpty()) {
        // there is a plugin that can support the exact file name, but we don't know a compatible version,
        // return null to force request to update cache
        return null
      }

      // Check if there's a plugin matching the extension

      fullExtension?.let { getByFilenameOrExt(it) }?.let {
        return it
      }

      if (fileType is PlainTextLikeFileType || fileType is DetectedByContentFileType) {
        return fullExtension?.let { cache.getIfPresent(it) }
               ?: if (fullExtension?.let { knownExtensions.get(it) }?.isNotEmpty() == true) {
                 // there is a plugin that can support the file type, but we don't know a compatible version,
                 // return null to force request to update cache
                 null
               }
               else {
                 PluginAdvertiserExtensionsData(fileName)
               }
      }
      return null
    }

    private fun isIgnored(extensionOrFileName: String): Boolean {
      return enabledExtensionOrFileNames.contains(extensionOrFileName)
             || unknownFeaturesCollector.isIgnored(createUnknownExtensionFeature(extensionOrFileName))
    }
  }
}
