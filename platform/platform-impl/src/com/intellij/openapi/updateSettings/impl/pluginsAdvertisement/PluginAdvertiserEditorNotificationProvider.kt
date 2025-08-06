// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextLikeFileType
import com.intellij.openapi.fileTypes.impl.DetectedByContentFileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserEditorNotificationProvider.AdvertiserSuggestion
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserExtensionsStateService.ExtensionDataProvider
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService.Companion.getSuggestedCommercialIdeCode
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService.Companion.isCommunityIde
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.ui.HyperlinkLabel
import fleet.util.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.BorderLayout
import java.util.*
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JLabel

@ApiStatus.Internal
@IntellijInternalApi
class PluginAdvertiserEditorNotificationProvider : EditorNotificationProvider, DumbAware {

  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment || tryUltimateIsDisabled()) {
      return null
    }

    val providedSuggestion = SUGGESTION_EP_NAME.extensionList.asSequence()
      .mapNotNull { it.getSuggestion(project, file) }
      .firstOrNull()

    val suggestionChoice = getSuggestionData(project = project,
                                             activeProductCode = service<ApplicationInfo>().build.productCode,
                                             file = file)

    // If no advertisement suggestions are found, schedule an update so we make sure that
    // plugin/IDE information is up to date the next time the file is opened.
    if (suggestionChoice.isError) {
      project.service<AdvertiserInfoUpdateService>().scheduleAdvertiserUpdate(file)
    }

    // If no suggestion was found of either kind, do not show any kind of notification.
    val suggestionData = suggestionChoice.valueOrNull
    if (providedSuggestion == null && suggestionData == null) {
      return null
    }

    return Function { editor ->
      // Plugin suggestions should take priority over IDE advertisements
      if (providedSuggestion != null) {
        logSuggestionShown(project, providedSuggestion.pluginIds.map { PluginId.getId(it) })
        providedSuggestion.apply(editor)
      }
      else {
        suggestionData?.apply(editor)?.let { panel ->
          logSuggestionShown(project, suggestionData.getSuggested())
          panel
        }
      }
    }
  }

  class AdvertiserSuggestion(
    private val project: Project,
    private val extensionOrFileName: String,
    foundPlugins: Set<PluginData>,
    allJetBrainsPluginsMarketplaceIds: Set<PluginId>,
    val suggestedIdes: List<SuggestedIde>,
    val overrideSuggestionText: @NlsContexts.Label String? = null,
    val unknownFeature: UnknownFeature? = null,
  ) : Function<FileEditor, EditorNotificationPanel?> {

    private var installedPlugin: IdeaPluginDescriptor? = null

    @VisibleForTesting
    val thirdParty: MutableSet<PluginData> = mutableSetOf()
    private val jbProduced: MutableSet<PluginData> = mutableSetOf()

    private val hasSuggestedIde: Boolean

    private var pluginsToInstall: Set<PluginData>? = null

    init {
      val descriptorsById = PluginManagerCore.buildPluginIdMap()
      for (data in foundPlugins) {
        val pluginId = data.pluginId

        if (pluginId in descriptorsById) {
          installedPlugin = descriptorsById[pluginId]
        }
        else if (!data.isBundled) {
          (if (allJetBrainsPluginsMarketplaceIds.contains(pluginId)) jbProduced else thirdParty) += data
        }
      }

      hasSuggestedIde = suggestedIdes.isNotEmpty()
                        && jbProduced.isEmpty()
                        && isMappedToTextMate(extensionOrFileName)
    }

    fun getSuggested(): Collection<PluginId> {
      if (hasSuggestedIde) return emptyList()

      return pluginsToInstall?.map { it.pluginId } ?: emptyList()
    }

    override fun apply(fileEditor: FileEditor): EditorNotificationPanel? {
      lateinit var label: JLabel
      val status = if (isCommunityIde()) EditorNotificationPanel.Status.Promo else EditorNotificationPanel.Status.Info
      val panel = object : EditorNotificationPanel(fileEditor, status) {
        init {
          label = myLabel
        }
      }

      val pluginAdvertiserExtensionsState = PluginAdvertiserExtensionsStateService.getInstance().createExtensionDataProvider(project)
      panel.text = overrideSuggestionText ?: IdeBundle.message("plugins.advertiser.plugins.found", extensionOrFileName)

      fun createInstallActionLabel(plugins: Set<PluginData>) {
        this.pluginsToInstall = plugins

        val labelText = plugins.singleOrNull()?.nullablePluginName?.let {
          IdeBundle.message("plugins.advertiser.action.install.plugin.name", it)
        } ?: IdeBundle.message("plugins.advertiser.action.install.plugins")

        panel.createActionLabel(labelText) {
          FUSEventSource.EDITOR.logInstallPlugins(plugins.map { it.pluginIdString })
          installAndEnable(project, plugins.mapTo(HashSet()) { it.pluginId }, true) {
            pluginAdvertiserExtensionsState.addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName)
            updateAllNotifications(project)
          }
        }
      }

      val installedPlugin = installedPlugin
      if (hasSuggestedIde) {
        addSuggestedIdes(panel, label, pluginAdvertiserExtensionsState)
        return panel // Don't show the "Ignore extension" label
      }
      else if (installedPlugin != null) {
        if (!installedPlugin.isEnabled) {
          if (pluginRequiresUltimatePluginButItsDisabled(installedPlugin.pluginId)) {
            // the plugin requires ultimate and it cannot be enabled
            return null
          }

          panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.enable.plugin", installedPlugin.name)) {
            pluginAdvertiserExtensionsState.addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName)
            updateAllNotifications(project)
            FUSEventSource.EDITOR.logEnablePlugins(listOf(installedPlugin.pluginId.idString), project)
            PluginManagerConfigurable.showPluginConfigurableAndEnable(project, setOf(installedPlugin))
          }
        }
        else {
          // The plugin supporting the pattern is installed and enabled, but the current file is reassigned to a different file type
          return null
        }
      }
      else if (suggestedIdes.isNotEmpty() && jbProduced.isEmpty()) {
        addSuggestedIdes(panel, label, pluginAdvertiserExtensionsState)
        return panel    // Don't show the "Ignore extension" label
      }
      else if (thirdParty.isNotEmpty() || jbProduced.isNotEmpty()) {
        createInstallActionLabel(jbProduced + thirdParty)
      }
      else {
        return null
      }

      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.extension")) {
        FUSEventSource.EDITOR.logIgnoreExtension(project)
        if (unknownFeature == null) {
          pluginAdvertiserExtensionsState.ignoreExtensionOrFileNameAndInvalidateCache(extensionOrFileName)
        }
        else {
          UnknownFeaturesCollector.getInstance(project).ignoreFeature(unknownFeature)
        }
        updateAllNotifications(project)
      }

      return panel
    }

    private fun isMappedToTextMate(extensionOrFileName: String): Boolean {
      if (!extensionOrFileName.startsWith("*.")) return false

      val fileType = FileTypeManager.getInstance().getFileTypeByExtension(extensionOrFileName.removePrefix("*."))
      return fileType is PlainTextLikeFileType
             && PluginAdvertiserService.reservedIdeExtensions.contains(extensionOrFileName)
    }

    private fun addSuggestedIdes(
      panel: EditorNotificationPanel,
      label: JLabel,
      pluginAdvertiserExtensionsState: ExtensionDataProvider,
    ) {
      logSuggestedProducts(project, suggestedIdes)

      if (suggestedIdes.size > 1) {
        val parentPanel = label.parent
        parentPanel.remove(label)
        val hyperlinkLabel = HyperlinkLabel().apply {
          setTextWithHyperlink(IdeBundle.message("plugins.advertiser.extensions.supported.in.ides", extensionOrFileName))
          addHyperlinkListener { FUSEventSource.EDITOR.learnMoreAndLog(project) }
        }
        parentPanel.add(hyperlinkLabel, BorderLayout.CENTER)
      }
      else {
        panel.text = IdeBundle.message("plugins.advertiser.extensions.supported.in.ultimate", extensionOrFileName,
                                       suggestedIdes.single().name)
      }

      for (suggestedIde in suggestedIdes) {
        val pluginId = guessPluginIdFromFile(extensionOrFileName)?.let { PluginId.getId(it) }
        panel.createTryUltimateActionLabel(suggestedIde, project, pluginId) {
          pluginAdvertiserExtensionsState.addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName)
        }
      }

      if (suggestedIdes.size == 1) {
        panel.createActionLabel(IdeBundle.message("plugins.advertiser.learn.more")) {
          FUSEventSource.EDITOR.learnMoreAndLog(project)
        }
      }

      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
        FUSEventSource.EDITOR.ignoreUltimateAndLog(project)
        updateAllNotifications(project)
      }
    }

    private fun guessPluginIdFromFile(extensionOrFileName: String): String? {
      // only some of the popular extensions
      return when (extensionOrFileName) {
        "*.css" -> "com.intellij.css"
        "*.go" -> "org.jetbrains.plugins.go"
        "*.js" -> "JavaScript"
        "*.jsx" -> "JavaScript"
        "*.php" -> "com.jetbrains.php"
        "*.rb" -> "org.jetbrains.plugins.ruby"
        "*.rs" -> "com.jetbrains.rust"
        "*.sql" -> "com.intellij.database"
        "*.ts" -> "JavaScript"
        "*.tsx" -> "JavaScript"
        "*.vue" -> "org.jetbrains.plugins.vue"
        else -> null
      }
    }
  }
}

private val SUGGESTION_EP_NAME: ExtensionPointName<PluginSuggestionProvider> = ExtensionPointName("com.intellij.pluginSuggestionProvider")

private val loggedPluginSuggestions: MutableCollection<PluginId> = Collections.synchronizedSet(HashSet())
private val loggedIdeSuggestions: MutableCollection<String> = Collections.synchronizedSet(HashSet())

private fun logSuggestionShown(project: Project, pluginIds: Collection<PluginId>) {
  for (pluginId in pluginIds) {
    if (!loggedPluginSuggestions.contains(pluginId)) {
      FUSEventSource.EDITOR.logPluginSuggested(project, pluginId)
      loggedPluginSuggestions.add(pluginId)
    }
  }
}

private fun logSuggestedProducts(project: Project, suggestedIdes: List<SuggestedIde>) {
  for (ide in suggestedIdes) {
    val productCode = ide.productCode
    if (!loggedIdeSuggestions.contains(productCode)) {
      FUSEventSource.EDITOR.logIdeSuggested(project, productCode)
      loggedIdeSuggestions.add(productCode)
    }
  }
}

private fun getSuggestionData(
  project: Project,
  activeProductCode: String,
  file: VirtualFile,
): Either<AdvertiserSuggestion?, NoSuchElementException> {
  val suggestion = PluginAdvertiserExtensionsStateService.getInstance().createExtensionDataProvider(project)
    .requestExtensionData(file)

  return when (suggestion) {
    null -> Either.error(NoSuchElementException())
    is NoSuggestions -> Either.value(null)
    is PluginAdvertisedByFileName -> Either.value(getSuggestionData(project, suggestion, activeProductCode, file.fileType))
    is PluginAdvertisedByFileContent -> Either.value(getSuggestionDataByDetector(project, suggestion))
  }
}

private fun getSuggestionDataByDetector(project: Project, suggestion: PluginAdvertisedByFileContent): AdvertiserSuggestion? {
  val implementationName = "${FILE_HANDLER_KIND}:${suggestion.fileHandler.id}"

  return AdvertiserSuggestion(
    project,
    implementationName,
    suggestion.plugins,
    emptySet(),
    emptyList(),
    IdeBundle.message("plugins.advertiser.plugins.file.handler.found", suggestion.fileHandler.displayName.get()),
    UnknownFeature(DEPENDENCY_SUPPORT_FEATURE, implementationName)
  )
}

@ApiStatus.Internal
@IntellijInternalApi
@TestOnly
fun getSuggestionData(
  project: Project,
  activeProductCode: String,
  fileName: String,
  fileType: FileType,
): AdvertiserSuggestion? {
  return service<PluginAdvertiserExtensionsStateService>()
    .createExtensionDataProvider(project)
    .requestExtensionData(fileName, fileType)
    ?.let { it as? PluginAdvertisedByFileName }
    ?.let { getSuggestionData(project = project, extensionsData = it, activeProductCode = activeProductCode, fileType = fileType) }
}

private fun getSuggestionData(
  project: Project,
  extensionsData: PluginAdvertisedByFileName,
  activeProductCode: String,
  fileType: FileType,
): AdvertiserSuggestion? {
  val marketplaceRequests = MarketplaceRequests.getInstance()
  val jbPluginsIds: Set<PluginId> = if (ApplicationManager.getApplication().isUnitTestMode) {
    emptySet()
  }
  else {
    marketplaceRequests.loadCachedJBPlugins() ?: return null
  }

  val ideExtensions = marketplaceRequests.extensionsForIdes ?: return null

  val extensionOrFileName = extensionsData.extensionOrFileName
  val dataSet = extensionsData.plugins

  val hasBundledPlugin = getBundledPluginToInstall(dataSet).isNotEmpty()
  val suggestedIdes = if (fileType is PlainTextLikeFileType || fileType is DetectedByContentFileType) {
    getSuggestedIdes(activeProductCode = activeProductCode,
                     extensionOrFileName = extensionOrFileName,
                     ideExtensions = ideExtensions).ifEmpty {
      if (hasBundledPlugin && !isIgnoreIdeSuggestion) listOf(PluginAdvertiserService.ideaUltimate) else emptyList()
    }
  }
  else {
    emptyList()
  }

  return AdvertiserSuggestion(project, extensionOrFileName, dataSet, jbPluginsIds, suggestedIdes)
}

private fun getSuggestedIdes(
  activeProductCode: String,
  extensionOrFileName: String,
  ideExtensions: Map<String, List<String>>,
): List<SuggestedIde> {
  if (isIgnoreIdeSuggestion) {
    return emptyList()
  }

  val productCodes = ideExtensions[extensionOrFileName]
  if (productCodes.isNullOrEmpty()) {
    return emptyList()
  }

  val suggestedIde = PluginAdvertiserService.ides.entries.firstOrNull { it.key in productCodes }
  val commercialVersionCode = getSuggestedCommercialIdeCode(activeProductCode)
  val commercialIde = PluginAdvertiserService.ides[commercialVersionCode]

  if (commercialVersionCode != null && suggestedIde != null && suggestedIde.key != commercialVersionCode &&
      // Don't suggest a commercial IDE if it doesn't support the extension.
      // We assume that IU supports all extensions, which is not true (e.g. *.cpp), but it's better than nothing.
      (commercialIde == PluginAdvertiserService.ideaUltimate || commercialVersionCode in productCodes)) {
    return listOf(commercialIde!!)
  }
  else if (suggestedIde != null && suggestedIde.key == activeProductCode) {
    return emptyList()
  }
  else {
    return suggestedIde?.value?.let { listOf(it) } ?: emptyList()
  }
}

private fun updateAllNotifications(project: Project) {
  EditorNotifications.getInstance(project).updateAllNotifications()
}

private val LOG: Logger = fileLogger()

@Service(Service.Level.PROJECT)
internal class AdvertiserInfoUpdateService(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
  private val mutex = Mutex()

  fun scheduleAdvertiserUpdate(file: VirtualFile) {
    val fileName = file.name
    coroutineScope.launch {
      mutex.withLock {
        val extensionsStateService = PluginAdvertiserExtensionsStateService.getInstance()
        var shouldUpdateNotifications = extensionsStateService.updateCache(fileName)
        val fullExtension = PluginAdvertiserExtensionsStateService.getFullExtension(fileName)
        if (fullExtension != null) {
          shouldUpdateNotifications = extensionsStateService.updateCache(fullExtension) || shouldUpdateNotifications
        }

        shouldUpdateNotifications = extensionsStateService.updateCompatibleFileHandlers() || shouldUpdateNotifications

        if (shouldUpdateNotifications) {
          withContext(Dispatchers.EDT) {
            updateAllNotifications(project)
          }
        }

        LOG.debug("Tried to update extensions cache for file '${fileName}'. shouldUpdateNotifications=$shouldUpdateNotifications")
      }
    }
  }
}