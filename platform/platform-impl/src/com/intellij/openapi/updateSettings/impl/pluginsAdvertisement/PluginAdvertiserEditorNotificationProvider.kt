// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextLikeFileType
import com.intellij.openapi.fileTypes.ex.DetectedByContentFileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.HyperlinkLabel
import java.awt.BorderLayout
import javax.swing.JLabel

class PluginAdvertiserEditorNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>(), DumbAware {
  override fun getKey(): Key<EditorNotificationPanel> = KEY

  override fun createNotificationPanel(file: VirtualFile,
                                       fileEditor: FileEditor,
                                       project: Project): EditorNotificationPanel? {
    val extensionsStateService = PluginAdvertiserExtensionsStateService.instance
    val suggestionData = getSuggestionData(project, ApplicationInfo.getInstance().build.productCode, file.name, file.fileType)
    if (suggestionData == null) {
      ProcessIOExecutorService.INSTANCE.execute {
        val marketplaceRequests = MarketplaceRequests.getInstance()
        marketplaceRequests.loadJetBrainsPluginsIds()
        marketplaceRequests.loadExtensionsForIdes()

        var shouldUpdateNotifications = extensionsStateService.updateCache(file.name)
        val fullExtension = PluginAdvertiserExtensionsStateService.getFullExtension(file.name)
        if (fullExtension != null) {
          shouldUpdateNotifications = extensionsStateService.updateCache(fullExtension) || shouldUpdateNotifications
        }
        if (shouldUpdateNotifications) {
          ApplicationManager.getApplication().invokeLater(
            { EditorNotifications.getInstance(project).updateNotifications(file) },
            project.disposed
          )
        }
        LOG.debug(String.format("Tried to update extensions cache for file '%s'. shouldUpdateNotifications=%s", file.name,
                                shouldUpdateNotifications))
      }
      return null
    }

    lateinit var label: JLabel
    val panel = object : EditorNotificationPanel(fileEditor) {
      init {
        label = myLabel
      }
    }

    val extensionOrFileName = suggestionData.extensionOrFileName
    val pluginAdvertiserExtensionsState = extensionsStateService.createExtensionDataProvider(project)
    panel.text = IdeBundle.message("plugins.advertiser.plugins.found", extensionOrFileName)
    val onPluginsInstalled = Runnable {
      pluginAdvertiserExtensionsState.addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName)
      updateAllNotifications(project)
    }

    val disabledPlugin = suggestionData.myDisabledPlugin
    if (disabledPlugin != null) {
      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.enable.plugin", disabledPlugin.name)) {
        pluginAdvertiserExtensionsState.addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName)
        updateAllNotifications(project)
        FUSEventSource.EDITOR.logEnablePlugins(listOf(disabledPlugin.pluginId.idString), project)
        PluginManagerConfigurable.showPluginConfigurableAndEnable(project, setOf(disabledPlugin))
      }
    }
    else if (suggestionData.myJbProduced.isNotEmpty()) {
      createInstallActionLabel(project, panel, suggestionData.myJbProduced, onPluginsInstalled)
    }
    else if (suggestionData.suggestedIdes.isNotEmpty()) {
      val suggestedIdes = suggestionData.suggestedIdes
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
        panel.text = IdeBundle.message("plugins.advertiser.extensions.supported.in.ultimate", extensionOrFileName, suggestedIdes.single().name)
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
      return panel    // Don't show the "Ignore extension" label
    }
    else if (!suggestionData.myThirdParty.isEmpty()) {
      createInstallActionLabel(project, panel, suggestionData.myThirdParty, onPluginsInstalled)
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

  data class SuggestedIde(val name: String, val downloadUrl: String)

  class AdvertiserSuggestion(val extensionOrFileName: String, dataSet: Set<PluginData>, jbPluginsIds: Set<String>, val suggestedIdes: List<SuggestedIde>) {
    var myDisabledPlugin: IdeaPluginDescriptor? = null
    val myJbProduced: MutableSet<PluginData> = HashSet()
    val myThirdParty: MutableSet<PluginData> = HashSet()

    init {
      val descriptorsById = PluginManagerCore.buildPluginIdMap()
      for (data in dataSet) {
        val installedPlugin: IdeaPluginDescriptor? = descriptorsById[data.pluginId]
        if (installedPlugin != null) {
          if (!installedPlugin.isEnabled && myDisabledPlugin == null) myDisabledPlugin = installedPlugin
        }
        else if (!data.isBundled) {
          myThirdParty.add(data)
          if (jbPluginsIds.contains(data.pluginIdString)) {
            myJbProduced.add(data)
          }
        }
      }
    }
  }

  companion object {
    private val KEY = Key.create<EditorNotificationPanel>("file.type.associations.detected")
    private val LOG: Logger = Logger.getInstance(PluginAdvertiserEditorNotificationProvider::class.java)

    fun getSuggestionData(project: Project, activeProductCode: String, fileName: String, fileType: FileType): AdvertiserSuggestion? {
      val extensionsStateService = PluginAdvertiserExtensionsStateService.instance
      val pluginAdvertiserExtensionsState = extensionsStateService.createExtensionDataProvider(project)
      val extensionsData = pluginAdvertiserExtensionsState.requestExtensionData(fileName, fileType) ?: return null

      val marketplaceRequests = MarketplaceRequests.getInstance()
      val jbPluginsIds = marketplaceRequests.jetBrainsPluginsIds ?: return null
      val ideExtensions = marketplaceRequests.extensionsForIdes ?: return null

      val extensionOrFileName = extensionsData.extensionOrFileName
      val dataSet = extensionsData.plugins

      val hasBundledPlugin = getBundledPluginToInstall(dataSet).isNotEmpty()
      val suggestedIdes = if (fileType is PlainTextLikeFileType || fileType is DetectedByContentFileType) {
        getSuggestedIdes(activeProductCode, extensionOrFileName, ideExtensions).ifEmpty {
          if (hasBundledPlugin) listOf(ideaUltimate) else emptyList()
        }
      }
      else
        emptyList()

      return AdvertiserSuggestion(extensionOrFileName, dataSet, jbPluginsIds, suggestedIdes)
    }

    private fun getSuggestedIdes(activeProductCode: String, extensionOrFileName: String, ideExtensions: Map<String, List<String>>): List<SuggestedIde> {
      if (isIgnoreIdeSuggestion) {
        return emptyList()
      }

      val productCodes = ideExtensions[extensionOrFileName]
      if (productCodes == null || productCodes.isEmpty()) {
        return emptyList()
      }

      val suggestedIde = ides.entries.firstOrNull { it.key in productCodes }
      val commercialVersionCode = when (activeProductCode) {
        "IC", "IE" -> "IU"
        "PC", "PE" -> "PY"
        else -> null
      }

      if (commercialVersionCode != null && suggestedIde != null && suggestedIde.key != commercialVersionCode) {
        return listOf(suggestedIde.value, ides[commercialVersionCode]!!)
      }
      else {
        return suggestedIde?.value?.let { listOf(it) } ?: emptyList()
      }
    }

    private fun createInstallActionLabel(project: Project,
                                         panel: EditorNotificationPanel,
                                         dataSet: Set<PluginData>,
                                         onSuccess: Runnable) {
      val pluginIds = dataSet.mapTo(mutableSetOf(), PluginData::pluginId)
      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.install.plugins")) {
        FUSEventSource.EDITOR.logInstallPlugins(pluginIds.map { it.idString })
        installAndEnable(project, pluginIds, true, onSuccess)
      }
    }

    private fun updateAllNotifications(project: Project) {
      EditorNotifications.getInstance(project).updateAllNotifications()
    }

    val ideaUltimate = SuggestedIde("IntelliJ IDEA Ultimate", "https://www.jetbrains.com/idea/download/")
    val pyCharmProfessional = SuggestedIde("PyCharm Professional", "https://www.jetbrains.com/pycharm/download/")

    private val ides = linkedMapOf(
      "WS" to SuggestedIde("WebStorm", "https://www.jetbrains.com/webstorm/download/"),
      "RM" to SuggestedIde("RubyMine", "https://www.jetbrains.com/ruby/download/"),
      "PY" to pyCharmProfessional,
      "PS" to SuggestedIde("PhpStorm", "https://www.jetbrains.com/phpstorm/download/"),
      "GO" to SuggestedIde("GoLand", "https://www.jetbrains.com/go/download/"),
      "CL" to SuggestedIde("CLion", "https://www.jetbrains.com/clion/download/"),
      "RD" to SuggestedIde("Rider", "https://www.jetbrains.com/rider/download/"),
      "OC" to SuggestedIde("AppCode", "https://www.jetbrains.com/objc/download/"),
      "IU" to ideaUltimate
    )
  }
}