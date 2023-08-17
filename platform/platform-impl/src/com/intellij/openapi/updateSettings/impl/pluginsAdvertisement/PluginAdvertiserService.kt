// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.advertiser.PluginFeatureCacheService
import com.intellij.ide.plugins.advertiser.PluginFeatureMap
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.org.PluginManagerFilters
import com.intellij.ide.ui.PluginBooleanOptionDescriptor
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService.Companion.ideaUltimate
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.MultiMap
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import kotlin.coroutines.coroutineContext

@ApiStatus.Internal
sealed interface PluginAdvertiserService {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PluginAdvertiserService = project.service()

    internal fun isCommunityIde(): Boolean {
      val thisProductCode = ApplicationInfoImpl.getShadowInstanceImpl().build.productCode
      return getSuggestedCommercialIdeCode(thisProductCode) != null
    }

    fun getSuggestedCommercialIdeCode(activeProductCode: String): String? {
      return when (activeProductCode) {
        "IC", "AS" -> "IU"
        "PC" -> "PY"
        else -> null
      }
    }

    fun getIde(ideCode: String?): SuggestedIde? = ides[ideCode]

    @Suppress("HardCodedStringLiteral")
    val ideaUltimate: SuggestedIde = SuggestedIde("IntelliJ IDEA Ultimate",
                                                  "https://www.jetbrains.com/idea/download/",
                                                  "https://www.jetbrains.com/idea/download/download-thanks.html?platform={type}")

    @Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
    private val pyCharmProfessional = SuggestedIde("PyCharm Professional",
                                                   "https://www.jetbrains.com/pycharm/download/",
                                                   "https://www.jetbrains.com/pycharm/download/download-thanks.html?platform={type}")

    @Suppress("HardCodedStringLiteral")
    internal val ides: Map<String, SuggestedIde> = linkedMapOf(
      "WS" to SuggestedIde("WebStorm", "https://www.jetbrains.com/webstorm/download/"),
      "RM" to SuggestedIde("RubyMine", "https://www.jetbrains.com/ruby/download/"),
      "PY" to pyCharmProfessional,
      "PS" to SuggestedIde("PhpStorm", "https://www.jetbrains.com/phpstorm/download/"),
      "GO" to SuggestedIde("GoLand", "https://www.jetbrains.com/go/download/"),
      "CL" to SuggestedIde("CLion", "https://www.jetbrains.com/clion/download/"),
      "RD" to SuggestedIde("Rider", "https://www.jetbrains.com/rider/download/"),
      "IU" to ideaUltimate
    )

    internal val marketplaceIdeCodes: Map<String, String> = linkedMapOf(
      "IU" to "idea",
      "IC" to "idea_ce",
      "IE" to "idea_edu",
      "PY" to "pycharm",
      "PC" to "pycharm_ce",
      "PE" to "pycharm_edu",
      "WS" to "webstorm",
      "GO" to "go",
      "CL" to "clion",
      "RD" to "rider",
      "RM" to "ruby",
      "AS" to "androidstudio"
    )
  }

  suspend fun run(
    customPlugins: List<PluginNode>,
    unknownFeatures: Collection<UnknownFeature>,
    includeIgnored: Boolean = false,
  )

  suspend fun fetch(
    customPlugins: List<PluginNode>,
    unknownFeatures: Collection<UnknownFeature>,
    includeIgnored: Boolean = false,
  ): List<IdeaPluginDescriptor>

  @ApiStatus.Internal
  fun collectDependencyUnknownFeatures(includeIgnored: Boolean = false)

  @ApiStatus.Internal
  fun rescanDependencies(block: suspend CoroutineScope.() -> Unit = {})
}

open class PluginAdvertiserServiceImpl(
  private val project: Project,
  private val cs: CoroutineScope,
) : PluginAdvertiserService {

  companion object {
    private val notificationManager = SingletonNotificationManager(notificationGroup.displayId, NotificationType.INFORMATION)
  }

  override suspend fun run(
    customPlugins: List<PluginNode>,
    unknownFeatures: Collection<UnknownFeature>,
    includeIgnored: Boolean,
  ) {
    cs.launch(Dispatchers.IO) {
      val (plugins, featuresMap) = fetchFeatures(unknownFeatures, includeIgnored)

      val descriptorsById = PluginManagerCore.buildPluginIdMap()
      val pluginManagerFilters = PluginManagerFilters.getInstance()
      val disabledDescriptors = plugins.asSequence()
        .map { it.pluginId }
        .mapNotNull { descriptorsById[it] }
        .filterNot { it.isEnabled }
        .filter { pluginManagerFilters.allowInstallingPlugin(it) }
        .filter { pluginManagerFilters.isPluginCompatible(it) }
        .toList()

      val suggestToInstall = if (plugins.isEmpty())
        emptyList()
      else
        fetchPluginSuggestions(
          pluginIds = plugins.asSequence().map { it.pluginId }.toSet(),
          customPlugins = customPlugins,
          org = pluginManagerFilters,
        )

      launch(Dispatchers.EDT) {
        notifyUser(
          bundledPlugins = getBundledPluginToInstall(plugins, descriptorsById),
          suggestionPlugins = suggestToInstall,
          disabledDescriptors = disabledDescriptors,
          featuresMap = featuresMap,
          allUnknownFeatures = unknownFeatures,
          dependencies = PluginFeatureCacheService.getInstance().dependencies,
          includeIgnored = includeIgnored,
        )
      }
    }
  }

  private suspend fun fetchFeatures(features: Collection<UnknownFeature>,
                                    includeIgnored: Boolean): Pair<MutableSet<PluginData>, MultiMap<PluginId, UnknownFeature>> {
    val featuresMap = MultiMap.createSet<PluginId, UnknownFeature>()
    val plugins = mutableSetOf<PluginData>()

    val dependencies = PluginFeatureCacheService.getInstance().dependencies
    val ignoredPluginSuggestionState = GlobalIgnoredPluginSuggestionState.getInstance()
    for (feature in features) {
      coroutineContext.ensureActive()
      val featureType = feature.featureType
      val implementationName = feature.implementationName
      val featurePluginData = PluginFeatureService.instance.getPluginForFeature(featureType, implementationName)

      val installedPluginData = featurePluginData?.pluginData

      fun putFeature(data: PluginData) {
        val pluginId = data.pluginId
        if (ignoredPluginSuggestionState.isIgnored(pluginId) && !includeIgnored) { // globally ignored
          LOG.info("Plugin is ignored by user, suggestion will not be shown: $pluginId")
          return
        }

        plugins += data
        featuresMap.putValue(pluginId, featurePluginData?.displayName?.let { feature.withImplementationDisplayName(it) } ?: feature)
      }

      if (installedPluginData != null) {
        putFeature(installedPluginData)
      }
      else if (featureType == DEPENDENCY_SUPPORT_FEATURE && dependencies != null) {
        dependencies.get(implementationName).forEach(::putFeature)
      }
      else {
        MarketplaceRequests.getInstance()
          .getFeatures(featureType, implementationName)
          .asSequence()
          .mapNotNull { it.toPluginData() }
          .forEach { putFeature(it) }
      }
    }

    return plugins to featuresMap
  }

  override suspend fun fetch(customPlugins: List<PluginNode>,
                             unknownFeatures: Collection<UnknownFeature>,
                             includeIgnored: Boolean): List<IdeaPluginDescriptor> {
    val (plugins, featuresMap) = fetchFeatures(unknownFeatures, includeIgnored)

    if (plugins.isEmpty()) {
      return emptyList()
    }

    val pluginIds = plugins.asSequence().map { it.pluginId }.toSet()
    val pluginManagerFilters = PluginManagerFilters.getInstance()

    val result = ArrayList<IdeaPluginDescriptor>(RepositoryHelper.mergePluginsFromRepositories(
      MarketplaceRequests.loadLastCompatiblePluginDescriptors(pluginIds),
      customPlugins,
      true,
    ).asSequence()
      .filter { pluginIds.contains(it.pluginId) }
      .filterNot { isBrokenPlugin(it) }
      .filter { pluginManagerFilters.allowInstallingPlugin(it) }
      .toList())

    for (compatibleUpdate in MarketplaceRequests.getLastCompatiblePluginUpdate(result.map { it.pluginId }.toSet())) {
      val node = result.find { it.pluginId.idString == compatibleUpdate.pluginId }
      if (node is PluginNode) {
        node.externalPluginId = compatibleUpdate.externalPluginId
        node.externalUpdateId = compatibleUpdate.externalUpdateId
        node.description = null
      }
    }

    val localPluginIdMap = PluginManagerCore.buildPluginIdMap()

    if (result.size < plugins.size) {
      pluginIds.filterNot { pluginId -> result.find { pluginId == it.pluginId } != null }.mapNotNullTo(result) {
        convertToNode(localPluginIdMap[it])
      }
    }

    result.removeAll { localPluginIdMap[it.pluginId]?.isEnabled == true }

    for (descriptor in result) {
      val features = featuresMap[descriptor.pluginId]
      if (features.isNotEmpty()) {
        val suggestedFeatures = features
          .filter { "dependency" == it.featureDisplayName }
          .map { getSuggestionReason(it) }

        if (suggestedFeatures.isNotEmpty()) {
          (descriptor as PluginNode).suggestedFeatures = suggestedFeatures
        }
      }
    }

    return result
  }

  private fun getSuggestionReason(it: UnknownFeature): @Nls String {
    val kind = it.implementationName.substringBefore(":")
    if (kind == "executable") {
      val executableName = it.implementationName.substringAfter(":")
      if (executableName.isNotBlank()) {
        return IdeBundle.message("plugins.configurable.suggested.features.executable", executableName)
      }
    }

    return IdeBundle.message("plugins.configurable.suggested.features.dependency", it.implementationDisplayName)
  }

  private fun convertToNode(descriptor: IdeaPluginDescriptor?): PluginNode? {
    if (descriptor == null) {
      return null
    }

    val node = PluginNode(descriptor.pluginId, descriptor.name, "0")
    node.description = descriptor.description
    node.changeNotes = descriptor.changeNotes
    node.version = descriptor.version
    node.vendor = descriptor.vendor
    node.organization = descriptor.organization
    node.dependencies = descriptor.dependencies
    node.isConverted = true

    return node
  }

  private fun fetchPluginSuggestions(
    pluginIds: Set<PluginId>,
    customPlugins: List<PluginNode>,
    org: PluginManagerFilters,
  ): List<PluginDownloader> {
    return RepositoryHelper.mergePluginsFromRepositories(
      MarketplaceRequests.loadLastCompatiblePluginDescriptors(pluginIds),
      customPlugins,
      true,
    ).asSequence()
      .filter { pluginIds.contains(it.pluginId) }
      .filterNot { PluginManagerCore.isDisabled(it.pluginId) }
      .filterNot { isBrokenPlugin(it) }
      .filter { loadedPlugin ->
        when (val installedPlugin = PluginManagerCore.getPluginSet().findInstalledPlugin(loadedPlugin.pluginId)) {
          null -> true
          else -> (!installedPlugin.isBundled || installedPlugin.allowBundledUpdate())
                  && PluginDownloader.compareVersionsSkipBrokenAndIncompatible(loadedPlugin.version, installedPlugin) > 0
        }
      }.filter { org.allowInstallingPlugin(it) }
      .map { PluginDownloader.createDownloader(it) }
      .toList()
  }

  private fun notifyUser(
    bundledPlugins: List<String>,
    suggestionPlugins: List<PluginDownloader>,
    disabledDescriptors: List<IdeaPluginDescriptorImpl>,
    featuresMap: MultiMap<PluginId, UnknownFeature>,
    allUnknownFeatures: Collection<UnknownFeature>,
    dependencies: PluginFeatureMap?,
    includeIgnored: Boolean,
  ) {
    val (notificationMessage, notificationActions) = if (suggestionPlugins.isNotEmpty() || disabledDescriptors.isNotEmpty()) {
      val action = if (disabledDescriptors.isEmpty()) {
        NotificationAction.createSimpleExpiring(IdeBundle.message("plugins.advertiser.action.configure.plugins")) {
          FUSEventSource.NOTIFICATION.logConfigurePlugins(project)

          PluginManagerConfigurable.showSuggestedPlugins(project, FUSEventSource.NOTIFICATION)
        }
      }
      else {
        val title = if (disabledDescriptors.size == 1)
          IdeBundle.message("plugins.advertiser.action.enable.plugin")
        else
          IdeBundle.message("plugins.advertiser.action.enable.plugins")

        NotificationAction.createSimpleExpiring(title) {
          cs.launch(Dispatchers.EDT) {
            FUSEventSource.NOTIFICATION.logEnablePlugins(
              disabledDescriptors.map { it.pluginId.idString },
              project,
            )

            PluginBooleanOptionDescriptor.togglePluginState(disabledDescriptors, true)
          }
        }
      }

      val notificationActions = listOf(
        action,
        createIgnoreUnknownFeaturesAction(suggestionPlugins, disabledDescriptors, allUnknownFeatures, dependencies),
      )
      val messagePresentation = getAddressedMessagePresentation(suggestionPlugins, disabledDescriptors, featuresMap)

      Pair(messagePresentation, notificationActions)
    }
    else if (bundledPlugins.isNotEmpty() && !isIgnoreIdeSuggestion) {
      IdeBundle.message(
        "plugins.advertiser.ultimate.features.detected",
        bundledPlugins.joinToString()
      ) to listOf(
        NotificationAction.createSimpleExpiring(
          IdeBundle.message("plugins.advertiser.action.try.ultimate", ideaUltimate.name)) {
          FUSEventSource.NOTIFICATION.openDownloadPageAndLog(project, ideaUltimate.downloadUrl)
        },
        NotificationAction.createSimpleExpiring(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
          FUSEventSource.NOTIFICATION.doIgnoreUltimateAndLog(project)
        },
      )
    }
    else {
      if (includeIgnored) {
        notificationGroup.createNotification(IdeBundle.message("plugins.advertiser.no.suggested.plugins"), NotificationType.INFORMATION)
          .setDisplayId("advertiser.no.plugins")
          .notify(project)
      }
      return
    }

    notificationManager.notify("", notificationMessage, project) {
      it.setSuggestionType(true).addActions(notificationActions as Collection<AnAction>)
    }
  }

  private fun createIgnoreUnknownFeaturesAction(
    plugins: Collection<PluginDownloader>,
    disabledPlugins: Collection<IdeaPluginDescriptor>,
    unknownFeatures: Collection<UnknownFeature>,
    dependencyPlugins: PluginFeatureMap?,
  ): NotificationAction {
    val ids = plugins.mapTo(LinkedHashSet()) { it.id } +
              disabledPlugins.map { it.pluginId }

    val message = IdeBundle.message(
      if (ids.size > 1) "plugins.advertiser.action.ignore.unknown.features" else "plugins.advertiser.action.ignore.unknown.feature")

    return NotificationAction.createSimpleExpiring(message) {
      FUSEventSource.NOTIFICATION.logIgnoreUnknownFeatures(project)

      val collector = UnknownFeaturesCollector.getInstance(project)
      for (unknownFeature in unknownFeatures) {
        if (unknownFeature.featureType != DEPENDENCY_SUPPORT_FEATURE
            || dependencyPlugins?.get(unknownFeature.implementationName)?.isNotEmpty() == true) {
          collector.ignoreFeature(unknownFeature)
        }
      }

      val globalIgnoredState = GlobalIgnoredPluginSuggestionState.getInstance()
      for (pluginIdToIgnore in ids) {
        globalIgnoredState.ignoreFeature(pluginIdToIgnore)
      }
    }
  }

  open fun getAddressedMessagePresentation(
    plugins: Collection<PluginDownloader>,
    disabledPlugins: Collection<IdeaPluginDescriptor>,
    features: MultiMap<PluginId, UnknownFeature>,
  ): @NotificationContent String {
    val ids = plugins.mapTo(LinkedHashSet()) { it.id } +
              disabledPlugins.map { it.pluginId }

    val pluginNames = (plugins.map { it.pluginName } + disabledPlugins.map { it.name })
      .sorted()
      .distinct()
      .joinToString(", ")

    val addressedFeatures = collectFeaturesByName(ids, features)

    val pluginsNumber = ids.size
    val repoPluginsNumber = plugins.size

    val entries = addressedFeatures.entrySet()
    return if (entries.size == 1) {
      val feature = entries.single()

      if (feature.key != "dependency") {
        IdeBundle.message(
          "plugins.advertiser.missing.feature",
          pluginsNumber,
          feature.key,
          feature.value.joinToString(),
          repoPluginsNumber,
          pluginNames
        )
      }
      else {
        if (feature.value.size <= 1) {
          IdeBundle.message(
            "plugins.advertiser.missing.feature.dependency",
            pluginsNumber,
            pluginNames
          )
        }
        else {
          IdeBundle.message(
            "plugins.advertiser.missing.features.dependency",
            pluginsNumber,
            pluginNames
          )
        }
      }
    }
    else {
      if (entries.all { it.key == "dependency" }) {
        IdeBundle.message(
          "plugins.advertiser.missing.features.dependency",
          pluginsNumber,
          pluginNames
        )
      }
      else {
        IdeBundle.message(
          "plugins.advertiser.missing.features",
          pluginsNumber,
          entries.joinToString(separator = "; ") { it.value.joinToString(prefix = it.key + ": ") },
          repoPluginsNumber,
          pluginNames
        )
      }
    }
  }

  override fun collectDependencyUnknownFeatures(includeIgnored: Boolean) {
    val featuresCollector = UnknownFeaturesCollector.getInstance(project)

    featuresCollector.getUnknownFeaturesOfType(DEPENDENCY_SUPPORT_FEATURE)
      .forEach { featuresCollector.unregisterUnknownFeature(it) }

    DependencyCollectorBean.EP_NAME.extensions
      .asSequence()
      .flatMap { dependencyCollectorBean ->
        dependencyCollectorBean.instance.collectDependencies(project).map { coordinate ->
          UnknownFeature(
            DEPENDENCY_SUPPORT_FEATURE,
            IdeBundle.message("plugins.advertiser.feature.dependency"),
            dependencyCollectorBean.kind + ":" + coordinate,
            null,
          )
        }
      }.forEach {
        featuresCollector.registerUnknownFeature(it)
      }
  }

  private fun collectFeaturesByName(
    ids: Set<PluginId>,
    features: MultiMap<PluginId, UnknownFeature>,
  ): MultiMap<String, String> {
    val result = MultiMap.createSet<String, String>()
    ids
      .flatMap { features[it] }
      .forEach { result.putValue(it.featureDisplayName, it.implementationDisplayName) }
    return result
  }

  override fun rescanDependencies(block: suspend CoroutineScope.() -> Unit) {
    cs.launch(Dispatchers.IO) {
      rescanDependencies()
      block()
    }
  }

  @RequiresBackgroundThread
  private suspend fun rescanDependencies() {
    collectDependencyUnknownFeatures()

    val dependencyUnknownFeatures = UnknownFeaturesCollector.getInstance(project).unknownFeatures
    if (dependencyUnknownFeatures.isNotEmpty()) {
      run(
        customPlugins = loadPluginsFromCustomRepositories(),
        unknownFeatures = dependencyUnknownFeatures,
      )
    }
  }
}

open class HeadlessPluginAdvertiserServiceImpl : PluginAdvertiserService {

  final override suspend fun run(
    customPlugins: List<PluginNode>,
    unknownFeatures: Collection<UnknownFeature>,
    includeIgnored: Boolean,
  ) {
  }

  override suspend fun fetch(customPlugins: List<PluginNode>,
                             unknownFeatures: Collection<UnknownFeature>,
                             includeIgnored: Boolean): List<IdeaPluginDescriptor> {
    return emptyList()
  }

  final override fun collectDependencyUnknownFeatures(includeIgnored: Boolean) {}

  final override fun rescanDependencies(block: suspend CoroutineScope.() -> Unit) {}
}

data class SuggestedIde(
  @NlsContexts.DialogMessage
  val name: String,
  val defaultDownloadUrl: String,
  val platformSpecificDownloadUrlTemplate: String? = null
) {
  val downloadUrl: String
    get() {
      return platformSpecificDownloadUrlTemplate?.let { OsArchMapper.getDownloadUrl(it) }
             ?: defaultDownloadUrl
    }
}

private object OsArchMapper {
  const val OS_ARCH_PARAMETER: String = "{type}"

  val DOWNLOAD_OS_ARCH_MAPPING: Map<Pair<OS, CpuArch>, String> = mapOf(
    (OS.Windows to CpuArch.X86_64) to "windows",
    (OS.Windows to CpuArch.ARM64) to "windowsARM64",
    (OS.macOS to CpuArch.ARM64) to "macM1",
    (OS.macOS to CpuArch.X86_64) to "mac",
    (OS.Linux to CpuArch.X86_64) to "linux",
    (OS.Linux to CpuArch.ARM64) to "linuxARM64",
  )

  fun getDownloadUrl(downloadUrlTemplate: String): String? {
    val os = OS.CURRENT
    val arch = CpuArch.CURRENT

    val osArchType = DOWNLOAD_OS_ARCH_MAPPING[(os to arch)] ?: return null
    if (downloadUrlTemplate.contains(OS_ARCH_PARAMETER)) {
      return downloadUrlTemplate.replace(OS_ARCH_PARAMETER, osArchType)
    }
    else {
      return downloadUrlTemplate + osArchType
    }
  }
}