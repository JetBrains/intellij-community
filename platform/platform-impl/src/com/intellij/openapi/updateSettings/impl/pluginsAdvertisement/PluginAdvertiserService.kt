// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.advertiser.PluginFeatureCacheService
import com.intellij.ide.plugins.advertiser.PluginFeatureMap
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.ui.PluginBooleanOptionDescriptor
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService.Companion.DEPENDENCY_SUPPORT_TYPE
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService.Companion.EXECUTABLE_DEPENDENCY_KIND
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService.Companion.ideaUltimate
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.UltimateInstallationService
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.MultiMap
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import kotlin.coroutines.coroutineContext

@ApiStatus.Internal
sealed interface PluginAdvertiserService {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PluginAdvertiserService = project.service()

    fun isCommunityIde(): Boolean {
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
    val ideaUltimate: SuggestedIde = SuggestedIde(
      name = "IntelliJ IDEA Ultimate",
      productCode = "IU",
      defaultDownloadUrl = "https://www.jetbrains.com/idea/download/",
      platformSpecificDownloadUrlTemplate = "https://www.jetbrains.com/idea/download/download-thanks.html?platform={type}",
      baseDownloadUrl = "https://download.jetbrains.com/idea/ideaIU"
    )

    @Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
    val pyCharmProfessional = SuggestedIde(
      name = "PyCharm Professional",
      productCode = "PY",
      defaultDownloadUrl = "https://www.jetbrains.com/pycharm/download/",
      platformSpecificDownloadUrlTemplate = "https://www.jetbrains.com/pycharm/download/download-thanks.html?platform={type}",
      baseDownloadUrl = "https://download.jetbrains.com/python/pycharm-professional"
    )

    @Suppress("HardCodedStringLiteral")
    internal val ides: Map<String, SuggestedIde> = listOf(
      SuggestedIde("WebStorm", "WS", "https://www.jetbrains.com/webstorm/download/"),
      SuggestedIde("RubyMine", "RM", "https://www.jetbrains.com/ruby/download/"),
      pyCharmProfessional,
      SuggestedIde("PhpStorm", "PS", "https://www.jetbrains.com/phpstorm/download/"),
      SuggestedIde("GoLand", "GO", "https://www.jetbrains.com/go/download/"),
      SuggestedIde("CLion", "CL", "https://www.jetbrains.com/clion/download/"),
      SuggestedIde("Rider", "RD", "https://www.jetbrains.com/rider/download/"),
      SuggestedIde("RustRover", "RR", "https://www.jetbrains.com/rust/download/"),
      ideaUltimate
    ).associateBy { it.productCode }

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
      "RR" to "rust",
      "AS" to "androidstudio"
    )

    internal const val EXECUTABLE_DEPENDENCY_KIND: String = "executable"
    internal const val DEPENDENCY_SUPPORT_TYPE: String = "dependencySupport"
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

      removeNonProjectSuggestions(plugins, featuresMap)

      val descriptorsById = PluginManagerCore.buildPluginIdMap()
      val disabledDescriptors = plugins.asSequence()
        .map { it.pluginId }
        .mapNotNull { descriptorsById[it] }
        .filterNot { it.isEnabled }
        .filter { PluginManagementPolicy.getInstance().canInstallPlugin(it) }
        .filter { isPluginCompatible(it) }
        .toList()

      val suggestToInstall = if (plugins.isEmpty()) {
        emptyList()
      }
      else {
        fetchPluginSuggestions(
          pluginIds = plugins.asSequence().map { it.pluginId }.toSet(),
          customPlugins = customPlugins
        )
      }

      launch {
        val dependencies = serviceAsync<PluginFeatureCacheService>().dependencies.get()
        withContext(Dispatchers.EDT) {
          notifyUser(
            bundledPlugins = getBundledPluginToInstall(plugins, descriptorsById),
            suggestionPlugins = suggestToInstall,
            disabledDescriptors = disabledDescriptors,
            featuresMap = featuresMap,
            allUnknownFeatures = unknownFeatures,
            dependencies = dependencies,
            includeIgnored = includeIgnored,
          )
        }
      }
    }
  }

  private fun removeNonProjectSuggestions(plugins: MutableSet<PluginData>, featuresMap: MultiMap<PluginId, UnknownFeature>) {
    // here we filter out suggestions that do not depend on Project contents, such as suggestions based on installed executable
    // we do not show notifications for them

    val nonProjectSuggestions = mutableListOf<Pair<PluginId, UnknownFeature>>()
    for (plugin in featuresMap.entrySet()) {
      for (feature in plugin.value) {
        if (feature.featureType == DEPENDENCY_SUPPORT_TYPE) {
          val kind = feature.implementationName.substringBefore(":")
          if (kind == EXECUTABLE_DEPENDENCY_KIND) {
            nonProjectSuggestions.add(plugin.key to feature)
          }
        }
      }
    }

    for (suggestion in nonProjectSuggestions) {
      featuresMap.remove(suggestion.first, suggestion.second)
    }

    plugins.removeIf { !featuresMap.containsKey(it.pluginId) }
  }

  /**
   * Checks if the plugin is compatible with the current build of the IDE.
   */
  private fun isPluginCompatible(descriptor: IdeaPluginDescriptor): Boolean {
    val incompatibilityReason = PluginManagerCore.checkBuildNumberCompatibility(descriptor, PluginManagerCore.buildNumber)
    return incompatibilityReason == null
  }

  private suspend fun fetchFeatures(features: Collection<UnknownFeature>,
                                    includeIgnored: Boolean): Pair<MutableSet<PluginData>, MultiMap<PluginId, UnknownFeature>> {
    val featuresMap = MultiMap.createSet<PluginId, UnknownFeature>()
    val plugins = mutableSetOf<PluginData>()

    val dependencies = serviceAsync<PluginFeatureCacheService>().dependencies.get()
    val ignoredPluginSuggestionState = serviceAsync<GlobalIgnoredPluginSuggestionState>()
    val pluginFeatureService = serviceAsync<PluginFeatureService>()
    for (feature in features) {
      coroutineContext.ensureActive()
      val featureType = feature.featureType
      val implementationName = feature.implementationName
      val featurePluginData = pluginFeatureService.getPluginForFeature(featureType, implementationName)

      val installedPluginData = featurePluginData?.pluginData

      fun putFeature(data: PluginData) {
        val pluginId = data.pluginId
        if (ignoredPluginSuggestionState.isIgnored(pluginId) && !includeIgnored) { // globally ignored
          LOG.info("Plugin is ignored by user, suggestion will not be shown: $pluginId")
          return
        }

        plugins.add(data)
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

    val result = ArrayList<IdeaPluginDescriptor>(RepositoryHelper.mergePluginsFromRepositories(
      MarketplaceRequests.loadLastCompatiblePluginDescriptors(pluginIds),
      customPlugins,
      true,
    ).asSequence()
      .filter { pluginIds.contains(it.pluginId) }
      .filterNot { isBrokenPlugin(it) }
                                                   .filter { PluginManagementPolicy.getInstance().canInstallPlugin(it) }
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
          .filter { it.featureType == DEPENDENCY_SUPPORT_TYPE }
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
    if (kind == EXECUTABLE_DEPENDENCY_KIND) {
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
    customPlugins: List<PluginNode>
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
      }.filter { PluginManagementPolicy.getInstance().canInstallPlugin(it) }
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
    for (plugin in suggestionPlugins) {
      FUSEventSource.NOTIFICATION.logPluginSuggested(project, plugin.id)
    }

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
          tryUltimate(pluginId = null, suggestedIde = ideaUltimate, project, FUSEventSource.NOTIFICATION)
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

    DependencyCollectorBean.EP_NAME.extensionList
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
        customPlugins = RepositoryHelper.loadPluginsFromCustomRepositories(null),
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
  val productCode: String,
  val defaultDownloadUrl: String,
  val platformSpecificDownloadUrlTemplate: String? = null,
  val baseDownloadUrl: String? = null
) {
  val downloadUrl: String
    get() {
      return platformSpecificDownloadUrlTemplate?.let { OsArchMapper.getDownloadUrl(it) }
             ?: defaultDownloadUrl
    }
}

fun tryUltimate(
  pluginId: PluginId?,
  suggestedIde: SuggestedIde,
  project: Project? = null,
  fusEventSource: FUSEventSource? = null,
) {
  if (Registry.`is`("ide.try.ultimate.automatic.installation")) {
    val existingProject = project ?: ProjectManager.getInstance().defaultProject
    existingProject.service<UltimateInstallationService>().install(pluginId, suggestedIde)
  } else {
    val eventSource = fusEventSource ?: FUSEventSource.EDITOR
    eventSource.openDownloadPageAndLog(project = project, url = suggestedIde.defaultDownloadUrl, pluginId = pluginId)
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
