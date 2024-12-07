// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.advertiser.PluginFeatureCacheService
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileTypes.FileNameMatcher
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeFactory
import com.intellij.openapi.fileTypes.PlainTextLikeFileType
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.fileTypes.impl.DetectedByContentFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.Strings
import com.intellij.platform.settings.CacheTag
import com.intellij.platform.settings.SettingsController
import com.intellij.platform.settings.objectSerializer
import com.intellij.platform.settings.settingDescriptorFactory
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.intellij.util.containers.mapSmartSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val LOG: Logger = fileLogger()

internal data class PluginAdvertiserExtensionsData(
  // Either extension or file name. Depends on which of the two properties has more priority for advertising plugins for this specific file.
  @JvmField val extensionOrFileName: String,
  @JvmField val plugins: Set<PluginData> = emptySet(),
)

/**
 * Stores locally installed plugins (both enabled and disabled) supporting given filenames/extensions.
 *
 * That's why we have to save it - if a plugin is disabled, then it will be not registered (we cannot rely on a marketplace only).
 */
@Serializable
private data class PluginAdvertiserExtensionsState(@JvmField val plugins: LinkedHashMap<String, PluginData> = LinkedHashMap())

@ApiStatus.Internal
@Service(Service.Level.APP)
class PluginAdvertiserExtensionsStateService : SettingsSavingComponent {
  companion object {
    fun getInstance(): PluginAdvertiserExtensionsStateService = service()

    fun getFullExtension(fileName: String): String? {
      return Strings.toLowerCase(FileUtilRt.getExtension(fileName)).takeIf { it.isNotEmpty() }?.let { "*.$it" }
    }
  }

  // this will be injected by ComponentManager (a client will request it from a coroutine scope as a service)
  private val settingDescriptorFactory = settingDescriptorFactory(PluginManagerCore.CORE_ID)

  private val pluginCache: LinkedHashMap<String, PluginData>
  private val isChanged = AtomicBoolean()

  private val settingDescriptor = settingDescriptorFactory.settingDescriptor(
    key = "pluginAdvertiserExtensions",
    serializer = settingDescriptorFactory.objectSerializer<PluginAdvertiserExtensionsState>(),
  ) {
    tags = listOf(CacheTag)
  }

  init {
    pluginCache = service<SettingsController>().getItem(settingDescriptor)?.plugins ?: LinkedHashMap()
  }

  override suspend fun save() {
    if (isChanged.compareAndSet(true, false)) {
      serviceAsync<SettingsController>().setItem(settingDescriptor, PluginAdvertiserExtensionsState(pluginCache))
    }
  }

  // Stores the marketplace plugins that support given filenames/extensions and are known to be compatible with
  // the current IDE build.
  private val cache = Caffeine
    .newBuilder()
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build<String, PluginAdvertiserExtensionsData>()

  fun createExtensionDataProvider(project: Project): ExtensionDataProvider = ExtensionDataProvider(project)

  fun registerLocalPlugin(matchers: List<FileNameMatcher>, descriptor: PluginDescriptor) {
    var changed = false
    for (matcher in matchers) {
      val newValue = PluginData(descriptor)
      val oldValue = pluginCache.put(matcher.presentableString, newValue)
      if (oldValue != newValue) {
        changed = true
      }
    }

    if (changed) {
      isChanged.set(true)
    }
  }

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  suspend fun updateCache(extensionOrFileName: String): Boolean {
    if (cache.getIfPresent(extensionOrFileName) != null) {
      return false
    }

    val knownExtensions = PluginFeatureCacheService.getInstance().extensions.get()
    if (knownExtensions == null) {
      LOG.debug("No known extensions loaded")
      return false
    }

    withContext(Dispatchers.IO) {
      val compatiblePlugins = requestCompatiblePlugins(extensionOrFileName, knownExtensions.get(extensionOrFileName))
      updateCache(extensionOrFileName, compatiblePlugins)
    }

    return true
  }

  @VisibleForTesting
  fun updateCache(extensionOrFileName: String, compatiblePlugins: Set<PluginData>) {
    cache.put(extensionOrFileName, PluginAdvertiserExtensionsData(extensionOrFileName, compatiblePlugins))
  }

  inner class ExtensionDataProvider(private val project: Project) {
    private val unknownFeaturesCollector get() = UnknownFeaturesCollector.getInstance(project)
    private val enabledExtensionOrFileNames = ConcurrentCollectionFactory.createConcurrentSet<String>()

    fun ignoreExtensionOrFileNameAndInvalidateCache(extensionOrFileName: String) {
      unknownFeaturesCollector.ignoreFeature(createUnknownExtensionFeature(extensionOrFileName))
      cache.invalidate(extensionOrFileName)
    }

    fun addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName: String) {
      enabledExtensionOrFileNames.add(extensionOrFileName)
      cache.invalidate(extensionOrFileName)
    }

    private fun getByFilenameOrExt(fileNameOrExtension: String): PluginAdvertiserExtensionsData? {
      return pluginCache.get(fileNameOrExtension)?.let {
        PluginAdvertiserExtensionsData(extensionOrFileName = fileNameOrExtension, plugins = java.util.Set.of(it))
      }
    }

    /**
     * Returns the list of plugins supporting a file with the specified name and file type.
     * The list includes both locally installed plugins (enabled and disabled) and plugins that can be installed
     * from the marketplace (if none of the installed plugins handle the given filename or extension).
     *
     * The return value of null indicates that the locally available data is not enough to produce a suggestion,
     * and we need to fetch up-to-date data from the marketplace.
     */
    internal fun requestExtensionData(fileName: String, fileType: FileType): PluginAdvertiserExtensionsData? {
      fun noSuggestions() = PluginAdvertiserExtensionsData(fileName, emptySet())

      val fullExtension = getFullExtension(fileName)
      if (fullExtension != null && isIgnored(fullExtension)) {
        LOG.debug { "Extension '$fullExtension' is ignored in project '${project.name}'" }
        return noSuggestions()
      }
      if (isIgnored(fileName)) {
        LOG.debug { "File '$fileName' is ignored in project '${project.name}'" }
        return noSuggestions()
      }

      if (fullExtension == null && fileType is FakeFileType) {
        return noSuggestions()
      }

      // Check if there's an installed plugin matching the exact file name
      getByFilenameOrExt(fileName)?.let {
        return it
      }

      val knownExtensions = PluginFeatureCacheService.getInstance().extensions.get()
      if (knownExtensions == null) {
        LOG.debug("No known extensions loaded")
        return null
      }

      val plugin = findEnabledPlugin(knownExtensions.get(fileName).mapTo(HashSet()) { it.pluginIdString })
      if (plugin != null) {
        // Plugin supporting the exact file name is installed and enabled, no advertiser is needed
        return noSuggestions()
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

      // Check if there's an installed plugin matching the extension
      fullExtension?.let { getByFilenameOrExt(it) }?.let {
        return it
      }

      if (fileType is PlainTextLikeFileType || fileType is DetectedByContentFileType) {
        if (fullExtension != null) {
          val knownCompatiblePlugins = cache.getIfPresent(fullExtension)
          if (knownCompatiblePlugins != null) {
            return knownCompatiblePlugins
          }

          if (knownExtensions.get(fullExtension).isNotEmpty()) {
            // there is a plugin that can support the file type, but we don't know a compatible version,
            // return null to force request to update cache
            return null
          }
        }

        // no extension and no plugins matching the exact name
        return noSuggestions()
      }
      return null
    }

    private fun isIgnored(extensionOrFileName: String): Boolean {
      return enabledExtensionOrFileNames.contains(extensionOrFileName)
             || unknownFeaturesCollector.isIgnored(createUnknownExtensionFeature(extensionOrFileName))
    }
  }
}

@RequiresBackgroundThread
@RequiresReadLockAbsence
private fun requestCompatiblePlugins(
  extensionOrFileName: String,
  dataSet: Set<PluginData>,
): Set<PluginData> {
  if (dataSet.isEmpty()) {
    LOG.debug { "No features for extension $extensionOrFileName" }
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

@Suppress("HardCodedStringLiteral", "DEPRECATION")
private fun createUnknownExtensionFeature(extensionOrFileName: String) = UnknownFeature(
  FileTypeFactory.FILE_TYPE_FACTORY_EP.name,
  "File Type",
  extensionOrFileName,
  extensionOrFileName,
)

private fun findEnabledPlugin(plugins: Set<String>): IdeaPluginDescriptor? {
  if (plugins.isEmpty()) {
    return null
  }
  else {
    return PluginManagerCore.loadedPlugins.find {
      it.isEnabled && plugins.contains(it.pluginId.idString)
    }
  }
}
