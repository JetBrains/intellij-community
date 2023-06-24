// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
import kotlinx.coroutines.*
import org.jetbrains.annotations.VisibleForTesting
import java.awt.BorderLayout
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.time.Duration.Companion.seconds

class PluginAdvertiserEditorNotificationProvider : EditorNotificationProvider, DumbAware {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    val suggestionData = getSuggestionData(project, ApplicationInfo.getInstance().build.productCode, file.name, file.fileType)

    if (suggestionData == null) {
      project.service<AdvertiserInfoUpdateService>()
        .scheduleAdvertiserUpdate(file)
      return null
    }

    val providedSuggestion = SUGGESTION_EP_NAME.extensionList.asSequence()
      .mapNotNull { it.getSuggestion(project, file) }
      .firstOrNull()

    if (providedSuggestion == null) {
      return suggestionData
    }

    return Function { editor ->
      suggestionData.apply(editor)
      ?: providedSuggestion.apply(editor)
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
    private val jbProduced = mutableSetOf<PluginData>()

    @VisibleForTesting
    val thirdParty: MutableSet<PluginData> = mutableSetOf()

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

    override fun apply(fileEditor: FileEditor): EditorNotificationPanel? {
      lateinit var label: JLabel
      val panel = object : EditorNotificationPanel(fileEditor, Status.Info) {
        init {
          label = myLabel
        }
      }

      val pluginAdvertiserExtensionsState = PluginAdvertiserExtensionsStateService.instance.createExtensionDataProvider(project)
      panel.text = IdeBundle.message("plugins.advertiser.plugins.found", extensionOrFileName)

      fun createInstallActionLabel(plugins: Set<PluginData>) {
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
      if (isCommunityIde() && isDefaultTextMatePlugin(extensionOrFileName) && suggestedIdes.isNotEmpty()) {
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
          // Plugin supporting the pattern is installed and enabled but the current file is reassigned to a different file type
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
             || "*.css" == extensionOrFileName
             || "*.php" == extensionOrFileName
             || "*.ruby" == extensionOrFileName
    }

    private fun addSuggestedIdes(panel: EditorNotificationPanel,
                                 label: JLabel,
                                 pluginAdvertiserExtensionsState: ExtensionDataProvider) {
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
        panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.try.ultimate", suggestedIde.name)) {
          pluginAdvertiserExtensionsState.addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName)
          FUSEventSource.EDITOR.openDownloadPageAndLog(project, suggestedIde.downloadUrl)
        }
      }

      if (suggestedIdes.size == 1) {
        panel.createActionLabel(IdeBundle.message("plugins.advertiser.learn.more")) {
          FUSEventSource.EDITOR.learnMoreAndLog(project)
        }
      }

      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
        FUSEventSource.EDITOR.doIgnoreUltimateAndLog(project)
        updateAllNotifications(project)
      }
    }
  }
}

private val SUGGESTION_EP_NAME: ExtensionPointName<PluginSuggestionProvider> = ExtensionPointName.create(
  "com.intellij.pluginSuggestionProvider")

@VisibleForTesting
fun getSuggestionData(
  project: Project,
  activeProductCode: String,
  fileName: String,
  fileType: FileType,
): PluginAdvertiserEditorNotificationProvider.AdvertiserSuggestion? {
  return PluginAdvertiserExtensionsStateService.instance
    .createExtensionDataProvider(project)
    .requestExtensionData(fileName, fileType)?.let {
      getSuggestionData(project, it, activeProductCode, fileType)
    }
}

private fun getSuggestionData(
  project: Project,
  extensionsData: PluginAdvertiserExtensionsData,
  activeProductCode: String,
  fileType: FileType,
): PluginAdvertiserEditorNotificationProvider.AdvertiserSuggestion? {
  val marketplaceRequests = MarketplaceRequests.getInstance()
  val jbPluginsIds = marketplaceRequests.loadCachedJBPlugins() ?: return null
  val ideExtensions = marketplaceRequests.extensionsForIdes ?: return null

  val extensionOrFileName = extensionsData.extensionOrFileName
  val dataSet = extensionsData.plugins

  val hasBundledPlugin = getBundledPluginToInstall(dataSet).isNotEmpty()
  val suggestedIdes = if (fileType is PlainTextLikeFileType || fileType is DetectedByContentFileType) {
    getSuggestedIdes(activeProductCode, extensionOrFileName, ideExtensions).ifEmpty {
      if (hasBundledPlugin && !isIgnoreIdeSuggestion) listOf(PluginAdvertiserService.ideaUltimate) else emptyList()
    }
  }
  else
    emptyList()

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

  if (commercialVersionCode != null && suggestedIde != null && suggestedIde.key != commercialVersionCode) {
    return listOf(PluginAdvertiserService.ides[commercialVersionCode]!!)
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

@Service(Service.Level.PROJECT)
internal class AdvertiserInfoUpdateService(
  private val project: Project,
  private val coroutineScope: CoroutineScope
) {
  fun scheduleAdvertiserUpdate(file: VirtualFile) {
    val fileName = file.name
    coroutineScope.launch {
      delay(30.seconds) // no hurry, let's think that the network is really slow anyway

      MarketplaceRequests.getInstance().updatePluginIdsAndExtensionData()

      val extensionsStateService = PluginAdvertiserExtensionsStateService.instance
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

      LOG.debug("Tried to update extensions cache for file '${fileName}'. shouldUpdateNotifications=$shouldUpdateNotifications")
    }
  }
}