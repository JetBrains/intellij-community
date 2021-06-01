// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.advertiser.PluginFeatureCacheService
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileTypes.FileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeFactory
import com.intellij.openapi.fileTypes.PlainTextLikeFileType
import com.intellij.openapi.fileTypes.ex.DetectedByContentFileType
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.mapSmartSet
import com.intellij.util.containers.orNull
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XMap
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal data class PluginAdvertiserExtensionsData(
  // Either extension or file name. Depends on which of the two properties has more priority for advertising plugins for this specific file.
  val extensionOrFileName: String,
  val plugins: Set<PluginData>,
)

@State(
  name = "PluginAdvertiserExtensions",
  storages = [Storage(StoragePathMacros.CACHE_FILE, roamingType = RoamingType.DISABLED)]
)
@Service(Service.Level.APP)
internal class PluginAdvertiserExtensionsStateService : SimplePersistentStateComponent<PluginAdvertiserExtensionsStateService.State>(
  State()
) {

  @Tag("pluginAdvertiserExtensions")
  class State : BaseState() {

    @get:XMap
    val plugins by linkedMap<String, PluginData>()

    operator fun set(fileNameOrExtension: String, descriptor: PluginDescriptor) {
      plugins[fileNameOrExtension] = PluginData(descriptor)

      // no need to waste time to check that map is really changed - registerLocalPlugin is not called often after start-up,
      // so, mod count will be not incremented too often
      incrementModificationCount()
    }

    operator fun get(fileNameOrExtension: String): PluginAdvertiserExtensionsData? {
      return plugins[fileNameOrExtension]?.let {
        PluginAdvertiserExtensionsData(fileNameOrExtension, setOf(it))
      }
    }
  }

  companion object {

    private val LOG = logger<PluginAdvertiserExtensionsStateService>()

    @JvmStatic
    val instance
      get() = service<PluginAdvertiserExtensionsStateService>()

    @JvmStatic
    fun getFullExtension(file: VirtualFile): String? = file.extension?.let { "*.$it" }

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
    .build<String, Optional<PluginAdvertiserExtensionsData>>()

  fun createExtensionDataProvider(project: Project) = ExtensionDataProvider(project)

  fun registerLocalPlugin(matcher: FileNameMatcher, descriptor: PluginDescriptor) {
    state[matcher.presentableString] = descriptor
  }

  fun updateCache(extensionOrFileName: String): Boolean {
    LOG.assertTrue(!ApplicationManager.getApplication().isReadAccessAllowed)
    LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread)

    if (cache.getIfPresent(extensionOrFileName) != null) {
      return false
    }

    val knownExtensions = PluginFeatureCacheService.instance.extensions
    if (knownExtensions == null) {
      LOG.debug("No known extensions loaded")
      return false
    }

    val compatiblePlugins = requestCompatiblePlugins(
      extensionOrFileName,
      knownExtensions[extensionOrFileName],
    )

    val optionalData = if (compatiblePlugins.isEmpty())
      Optional.empty()
    else
      Optional.of(PluginAdvertiserExtensionsData(extensionOrFileName, compatiblePlugins))

    cache.put(extensionOrFileName, optionalData)
    return true
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

    fun requestExtensionData(file: VirtualFile): PluginAdvertiserExtensionsData? {
      val fullExtension = getFullExtension(file)
      if (fullExtension != null && isIgnored(fullExtension)) {
        LOG.debug("Extension '$fullExtension' is ignored in project '${project.name}'")
        return null
      }
      val fileName = file.name
      if (isIgnored(fileName)) {
        LOG.debug("File '$fileName' is ignored in project '${project.name}'")
        return null
      }

      val fileType = file.fileType
      if (fullExtension == null && fileType is FakeFileType) {
        return null
      }

      state[fileName]?.let {
        return it
      }
      fullExtension?.let { state[it] }?.let {
        return it
      }

      val knownExtensions = PluginFeatureCacheService.instance.extensions
      if (knownExtensions == null) {
        LOG.debug("No known extensions loaded")
        return null
      }

      val optionalData = if (fileType is PlainTextLikeFileType
                             || fileType is DetectedByContentFileType) {
        fullExtension?.let { cache.getIfPresent(it) }
        ?: cache.getIfPresent(fileName)
      }
      else {
        val plugin = findEnabledPlugin(knownExtensions[fileName].map { it.pluginIdString }.toSet())
        LOG.debug {
          val suffix = plugin?.let { "by fileName via '${it.name}'(id: '${it.pluginId}') plugin" }
                       ?: "therefore looking only for plugins exactly matching fileName"
          "File '$fileName' (type: '$fileType') is already supported $suffix"
        }

        if (plugin != null) null else cache.getIfPresent(fileName)
      }

      return optionalData?.orNull()
    }

    private fun isIgnored(extensionOrFileName: String): Boolean {
      return enabledExtensionOrFileNames.contains(extensionOrFileName)
             || unknownFeaturesCollector.isIgnored(createUnknownExtensionFeature(extensionOrFileName))
    }
  }
}
