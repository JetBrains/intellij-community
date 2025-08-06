// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.advertiser.PluginFeatureCacheService
import com.intellij.ide.plugins.advertiser.PluginFeatureMap
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUiModelBuilderFactory
import com.intellij.ide.ui.PluginBooleanOptionDescriptor
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService.Companion.DEPENDENCY_SUPPORT_TYPE
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService.Companion.EXECUTABLE_DEPENDENCY_KIND
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService.Companion.ideaUltimate
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install.UltimateInstallationService
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.MultiMap
import com.intellij.util.io.computeDetached
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import kotlin.coroutines.coroutineContext

@ApiStatus.Internal
@IntellijInternalApi
sealed interface PluginAdvertiserService {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PluginAdvertiserService = project.service()

    fun isCommunityIde(): Boolean {
      val thisProductCode = ApplicationInfoImpl.getShadowInstanceImpl().build.productCode
      return getSuggestedCommercialIdeCode(thisProductCode) != null
    }

    @JvmStatic
    fun getSuggestedCommercialIdeCode(activeProductCode: String): String? {
      return when (activeProductCode) {
        "IC", "AI" -> "IU"
        "PC" -> "PY"
        else -> null
      }
    }

    @JvmStatic
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
    val pyCharmProfessional: SuggestedIde = SuggestedIde(
      name = "PyCharm",
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
      "PS" to "phpstorm",
      "GO" to "go",
      "CL" to "clion",
      "RD" to "rider",
      "RM" to "ruby",
      "RR" to "rust",
      "AI" to "androidstudio",
      "QA" to "aqua",
      "DB" to "datagrip"
    )

    internal const val EXECUTABLE_DEPENDENCY_KIND: String = "executable"
    internal const val DEPENDENCY_SUPPORT_TYPE: String = "dependencySupport"

    val reservedIdeExtensions: Set<String> = setOf(
      "*.c", "*.cs", "*.cpp", "*.css", "*.js",
      "*.groovy", "*.kt", "*.php", "*.rs",
      "*.ruby", "*.scala", "*.sql", "*.ts", "*.java"
    )
  }

  suspend fun run(
    customPlugins: List<PluginNode>,
    unknownFeatures: Collection<UnknownFeature>,
    includeIgnored: Boolean = false,
  )

  suspend fun fetch(
    customPlugins: List<PluginUiModel>,
    unknownFeatures: Collection<UnknownFeature>,
    includeIgnored: Boolean = false,
  ): List<PluginUiModel>

  @ApiStatus.Internal
  suspend fun collectDependencyUnknownFeatures(includeIgnored: Boolean = false)

  @ApiStatus.Internal
  fun rescanDependencies(block: suspend CoroutineScope.() -> Unit = {})
}

@OptIn(IntellijInternalApi::class, DelicateCoroutinesApi::class)
@IntellijInternalApi
open class PluginAdvertiserServiceImpl(
  private val project: Project,
  private val cs: CoroutineScope,
) : PluginAdvertiserService {

  private val notificationManager = SingletonNotificationManager(getPluginSuggestionNotificationGroup().displayId, NotificationType.INFORMATION)

  private val builderFactory = PluginUiModelBuilderFactory.getInstance()

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

  private suspend fun fetchFeatures(
    features: Collection<UnknownFeature>,
    includeIgnored: Boolean,
  ): Pair<MutableSet<PluginData>, MultiMap<PluginId, UnknownFeature>> {
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
          thisLogger().info("Plugin is ignored by user, suggestion will not be shown: $pluginId")
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
        val marketplaceFeatures = MarketplaceRequests.getInstance().getFeatures(featureType, implementationName)
        marketplaceFeatures
          .asSequence()
          .mapNotNull { it.toPluginData() }
          .forEach { putFeature(it) }
      }
    }

    return plugins to featuresMap
  }

  override suspend fun fetch(
    customPlugins: List<PluginUiModel>,
    unknownFeatures: Collection<UnknownFeature>,
    includeIgnored: Boolean,
  ): List<PluginUiModel> {
    val (plugins, featuresMap) = fetchFeatures(unknownFeatures, includeIgnored)

    if (plugins.isEmpty()) {
      return emptyList()
    }

    val pluginIds = plugins.asSequence().map { it.pluginId }.toSet()

    val lastCompatiblePluginDescriptors = computeDetached { MarketplaceRequests.loadLastCompatiblePluginModels(pluginIds) }
    val result = ArrayList<PluginUiModel>(RepositoryHelper.mergePluginModelsFromRepositories(
      lastCompatiblePluginDescriptors,
      customPlugins,
      true,
    ).asSequence()
      .filter { pluginIds.contains(it.pluginId) }
      .filterNot { isBrokenPlugin(it.pluginId, it.version) }
      .filter { PluginManagementPolicy.getInstance().canInstallPlugin(it.getDescriptor()) }
      .toList())

    val lastCompatiblePluginUpdate = computeDetached { MarketplaceRequests.getLastCompatiblePluginUpdate(result.map { it.pluginId }.toSet()) }
    for (compatibleUpdate in lastCompatiblePluginUpdate) {
      val node = result.find { it.pluginId.idString == compatibleUpdate.pluginId }
      if (node?.isFromMarketplace == true) {
        node.externalPluginId = compatibleUpdate.externalPluginId
        node.externalUpdateId = compatibleUpdate.externalUpdateId
        node.description = null
      }
    }

    val localPluginIdMap = PluginManagerCore.buildPluginIdMap()

    if (result.size < plugins.size) {
      pluginIds.filterNot { pluginId -> result.find { pluginId == it.pluginId } != null }.mapNotNullTo(result) {
        convertToModel(localPluginIdMap[it])
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
          (descriptor.getDescriptor() as? PluginNode)?.suggestedFeatures = suggestedFeatures
        }
      }
    }

    return result
  }

  private fun getSuggestionReason(it: UnknownFeature): @Nls String {
    return it.suggestionReason ?: IdeBundle.message("plugins.configurable.suggested.features.dependency", it.implementationDisplayName)
  }

  private fun convertToModel(descriptor: IdeaPluginDescriptor?): PluginUiModel? {
    if (descriptor == null) {
      return null
    }

    val builder = builderFactory.createBuilder(descriptor.pluginId)
    .setName(descriptor.name)
    .setSize("0")
    .setDescription(descriptor.description)
    .setChangeNotes(descriptor.changeNotes)
    .setVersion(descriptor.version)
    .setVendor(descriptor.vendor)
    .setVendorDetails(descriptor.organization)
    .setIsConverted(true)

    descriptor.dependencies.forEach { builder.addDependency(it.pluginId.idString, it.isOptional) }
    return builder.build()
  }

  private suspend fun fetchPluginSuggestions(
    pluginIds: Set<PluginId>,
    customPlugins: List<PluginNode>,
  ): List<PluginDownloader> {
    val lastCompatiblePluginDescriptors = computeDetached { MarketplaceRequests.loadLastCompatiblePluginDescriptors(pluginIds) }
    return RepositoryHelper.mergePluginsFromRepositories(
      lastCompatiblePluginDescriptors,
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

    val promoteDisabledPlugins = if (PluginManagerCore.isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID)) emptyList() else disabledDescriptors
    val (notificationMessage, notificationActions) = if (suggestionPlugins.isNotEmpty() || promoteDisabledPlugins.isNotEmpty()) {
      val action = if (promoteDisabledPlugins.isEmpty()) {
        NotificationAction.createSimpleExpiring(IdeBundle.message("plugins.advertiser.action.configure.plugins")) {
          FUSEventSource.NOTIFICATION.logConfigurePlugins(project)

          PluginManagerConfigurable.showSuggestedPlugins(project, FUSEventSource.NOTIFICATION)
        }
      }
      else {
        val title = if (promoteDisabledPlugins.size == 1)
          IdeBundle.message("plugins.advertiser.action.enable.plugin")
        else
          IdeBundle.message("plugins.advertiser.action.enable.plugins")

        NotificationAction.createSimpleExpiring(title) {
          cs.launch(Dispatchers.EDT) {
            FUSEventSource.NOTIFICATION.logEnablePlugins(
              promoteDisabledPlugins.map { it.pluginId.idString },
              project,
            )

            PluginBooleanOptionDescriptor.togglePluginState(promoteDisabledPlugins, true)
          }
        }
      }

      val notificationActions = listOf(
        action,
        createIgnoreUnknownFeaturesAction(suggestionPlugins, promoteDisabledPlugins, allUnknownFeatures, dependencies),
      )
      val messagePresentation = getAddressedMessagePresentation(suggestionPlugins, promoteDisabledPlugins, featuresMap)

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
          FUSEventSource.NOTIFICATION.ignoreUltimateAndLog(project)
        },
      )
    }
    else {
      if (includeIgnored) {
        getPluginSuggestionNotificationGroup()
          .createNotification(IdeBundle.message("plugins.advertiser.no.suggested.plugins"), NotificationType.INFORMATION)
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
        IdeBundle.message(
          "plugins.advertiser.missing.features.dependency",
          pluginsNumber,
          pluginNames
        )
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

  override suspend fun collectDependencyUnknownFeatures(includeIgnored: Boolean) {
    val featuresCollector = UnknownFeaturesCollector.getInstance(project)

    featuresCollector.getUnknownFeaturesOfType(DEPENDENCY_SUPPORT_FEATURE)
      .forEach { featuresCollector.unregisterUnknownFeature(it) }

    for (extension in DependencyCollectorBean.EP_NAME.extensionList) {
      for (dependency in extension.instance.collectDependencies(project)) {
        featuresCollector.registerUnknownFeature(UnknownFeature(
          DEPENDENCY_SUPPORT_FEATURE,
          IdeBundle.message("plugins.advertiser.feature.dependency"),
          extension.kind + ":" + dependency.coordinate,
          null,
          dependency.reason,
        ))
      }
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
        customPlugins = computeDetached { RepositoryHelper.loadPluginsFromCustomRepositories(null) },
        unknownFeatures = dependencyUnknownFeatures,
      )
    }
  }
}

@ApiStatus.Internal
@IntellijInternalApi
open class HeadlessPluginAdvertiserServiceImpl : PluginAdvertiserService {

  final override suspend fun run(
    customPlugins: List<PluginNode>,
    unknownFeatures: Collection<UnknownFeature>,
    includeIgnored: Boolean,
  ) {
  }

  override suspend fun fetch(
    customPlugins: List<PluginUiModel>,
    unknownFeatures: Collection<UnknownFeature>,
    includeIgnored: Boolean,
  ): List<PluginUiModel> {
    return emptyList()
  }

  final override suspend fun collectDependencyUnknownFeatures(includeIgnored: Boolean) {}

  final override fun rescanDependencies(block: suspend CoroutineScope.() -> Unit) {}
}

@ApiStatus.Internal
data class SuggestedIde(
  @NlsContexts.DialogMessage
  val name: String,
  val productCode: String,
  val defaultDownloadUrl: String,
  val platformSpecificDownloadUrlTemplate: String? = null,
  val baseDownloadUrl: String? = null,
) {
  val downloadUrl: String
    get() {
      return platformSpecificDownloadUrlTemplate?.let { OsArchMapper.getDownloadUrl(it) }
             ?: defaultDownloadUrl
    }
}

private const val TRY_ULTIMATE_DISABLED_KEY = "ide.try.ultimate.disabled"
private fun setTryUltimateKey(project: Project, value: Boolean) {
  PropertiesComponent.getInstance().setValue(TRY_ULTIMATE_DISABLED_KEY, value)
  EditorNotifications.getInstance(project).updateAllNotifications()
}

@ApiStatus.Internal
fun disableTryUltimate(project: Project) = setTryUltimateKey(project, true)

@ApiStatus.Internal
fun enableTryUltimate(project: Project) = setTryUltimateKey(project, false)

@ApiStatus.Internal
fun tryUltimateIsDisabled(): Boolean = PropertiesComponent.getInstance().getBoolean(TRY_ULTIMATE_DISABLED_KEY)

@ApiStatus.Internal
fun tryUltimate(
  pluginId: PluginId?,
  suggestedIde: SuggestedIde,
  project: Project?,
  fusEventSource: FUSEventSource? = null,
  fallback: (() -> Unit)? = null,
) {
  val eventSource = fusEventSource ?: FUSEventSource.EDITOR
  if (Registry.`is`("ide.try.ultimate.automatic.installation") && project != null) {
    eventSource.logTryUltimate(project, pluginId)
    project.service<UltimateInstallationService>().install(pluginId, suggestedIde, eventSource)
  }
  else {
    fallback?.invoke() ?: eventSource.openDownloadPageAndLog(project = project,
                                                             url = suggestedIde.defaultDownloadUrl,
                                                             suggestedIde = suggestedIde,
                                                             pluginId = pluginId)
  }
}

@ApiStatus.Internal
fun EditorNotificationPanel.createTryUltimateActionLabel(
  suggestedIde: SuggestedIde,
  project: Project,
  pluginId: PluginId? = null,
  action: (() -> Unit)? = null,
) {
  val labelName = IdeBundle.message("plugins.advertiser.action.try.ultimate", suggestedIde.name)
  createActionLabel(labelName) {
    action?.invoke()
    tryUltimate(pluginId, suggestedIde, project)
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
