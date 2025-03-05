// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextLikeFileType
import com.intellij.openapi.fileTypes.impl.DetectedByContentFileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserExtensionsStateService.ExtensionDataProvider
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService.Companion.getSuggestedCommercialIdeCode
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService.Companion.isCommunityIde
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.ui.HyperlinkLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.awt.BorderLayout
import java.util.*
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JLabel

@ApiStatus.Internal
class PluginAdvertiserEditorNotificationProvider : EditorNotificationProvider, DumbAware {

  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment || tryUltimateIsDisabled()) {
      return null
    }

    val providedSuggestion = SUGGESTION_EP_NAME.extensionList.asSequence()
      .mapNotNull { it.getSuggestion(project, file) }
      .firstOrNull()

    val suggestionData = getSuggestionData(project = project,
                                           activeProductCode = service<ApplicationInfo>().build.productCode,
                                           fileName = file.name,
                                           fileType = file.fileType)

    // If no advertisement suggestions are found, schedule an advertiser update so we make sure that
    // plugin/IDE information is up to date next time the file is opened.
    if (suggestionData == null) {
      project.service<AdvertiserInfoUpdateService>().scheduleAdvertiserUpdate(file)
    }

    // If no suggestion was found of either kind, do not show any kind of notification.
    if (providedSuggestion == null && suggestionData == null) {
      return null
    }

    return Function { editor ->
      // Plugin suggestions should take priority over IDE advertisements
      if (providedSuggestion != null) {
        logSuggestionShown(project, providedSuggestion.pluginIds.map { PluginId.getId(it) })
        providedSuggestion.apply(editor)
      } else {
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
    dataSet: Set<PluginData>,
    jbPluginsIds: Set<PluginId>,
    val suggestedIdes: List<SuggestedIde>,
  ) : Function<FileEditor, EditorNotificationPanel?> {

    private var installedPlugin: IdeaPluginDescriptor? = null
    private val jbProduced: MutableSet<PluginData> = mutableSetOf()
    private val hasSuggestedIde: Boolean = isCommunityIde() && isDefaultTextMatePlugin(extensionOrFileName) && suggestedIdes.isNotEmpty()

    @VisibleForTesting
    val thirdParty: MutableSet<PluginData> = mutableSetOf()

    private var pluginsToInstall: Set<PluginData>? = null

    init {
      val descriptorsById = PluginManagerCore.buildPluginIdMap()
      for (data in dataSet) {
        val pluginId = data.pluginId

        if (pluginId in descriptorsById) {
          installedPlugin = descriptorsById[pluginId]
        }
        else if (!data.isBundled) {
          (if (jbPluginsIds.contains(pluginId)) jbProduced else thirdParty) += data
        }
      }
    }

    fun getSuggested(): Collection<PluginId> {
      if (hasSuggestedIde) return emptyList()

      return pluginsToInstall?.map { it.pluginId } ?: emptyList()
    }

    override fun apply(fileEditor: FileEditor): EditorNotificationPanel? {
      lateinit var label: JLabel
      val status = if (hasSuggestedIde) EditorNotificationPanel.Status.Promo else EditorNotificationPanel.Status.Info
      val panel = object : EditorNotificationPanel(fileEditor, status) {
        init {
          label = myLabel
        }
      }

      val pluginAdvertiserExtensionsState = PluginAdvertiserExtensionsStateService.getInstance().createExtensionDataProvider(project)
      panel.text = IdeBundle.message("plugins.advertiser.plugins.found", extensionOrFileName)

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
        return panel    // Don't show the "Ignore extension" label
      }
      else if (installedPlugin != null) {
        if (!installedPlugin.isEnabled) {
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
        pluginAdvertiserExtensionsState.ignoreExtensionOrFileNameAndInvalidateCache(extensionOrFileName)
        updateAllNotifications(project)
      }

      return panel
    }

    private fun isDefaultTextMatePlugin(extensionOrFileName: String): Boolean {
      // there are registered file types for them even in Community Editions
      return "*.sql" == extensionOrFileName
             || "*.js" == extensionOrFileName
             || "*.ts" == extensionOrFileName
             || "*.css" == extensionOrFileName
             || "*.php" == extensionOrFileName
             || "*.ruby" == extensionOrFileName
    }

    private fun addSuggestedIdes(panel: EditorNotificationPanel,
                                 label: JLabel,
                                 pluginAdvertiserExtensionsState: ExtensionDataProvider) {
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

@ApiStatus.Internal
@VisibleForTesting
fun getSuggestionData(
  project: Project,
  activeProductCode: String,
  fileName: String,
  fileType: FileType,
): PluginAdvertiserEditorNotificationProvider.AdvertiserSuggestion? {
  return service<PluginAdvertiserExtensionsStateService>()
    .createExtensionDataProvider(project)
    .requestExtensionData(fileName, fileType)
    ?.let { getSuggestionData(project = project, extensionsData = it, activeProductCode = activeProductCode, fileType = fileType) }
}

private fun getSuggestionData(
  project: Project,
  extensionsData: PluginAdvertiserExtensionsData,
  activeProductCode: String,
  fileType: FileType,
): PluginAdvertiserEditorNotificationProvider.AdvertiserSuggestion? {
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

  return PluginAdvertiserEditorNotificationProvider.AdvertiserSuggestion(project, extensionOrFileName, dataSet, jbPluginsIds, suggestedIdes)
}

private fun getSuggestedIdes(activeProductCode: String,
                             extensionOrFileName: String,
                             ideExtensions: Map<String, List<String>>): List<SuggestedIde> {
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
  private val coroutineScope: CoroutineScope
) {
  fun scheduleAdvertiserUpdate(file: VirtualFile) {
    val fileName = file.name
    coroutineScope.launch {
      val extensionsStateService = PluginAdvertiserExtensionsStateService.getInstance()
      var shouldUpdateNotifications = extensionsStateService.updateCache(fileName)
      val fullExtension = PluginAdvertiserExtensionsStateService.getFullExtension(fileName)
      if (fullExtension != null) {
        shouldUpdateNotifications = extensionsStateService.updateCache(fullExtension) || shouldUpdateNotifications
      }

      if (shouldUpdateNotifications) {
        withContext(Dispatchers.EDT) {
          EditorNotifications.getInstance(project).updateNotifications(file)
        }
      }

      LOG.debug { "Tried to update extensions cache for file '${fileName}'. shouldUpdateNotifications=$shouldUpdateNotifications" }
    }
  }
}