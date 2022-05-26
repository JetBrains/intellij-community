// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextLikeFileType
import com.intellij.openapi.fileTypes.impl.DetectedByContentFileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.ui.HyperlinkLabel
import org.jetbrains.annotations.VisibleForTesting
import java.awt.BorderLayout
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JLabel

class PluginAdvertiserEditorNotificationProvider : EditorNotificationProvider,
                                                   DumbAware {

  override fun collectNotificationData(
    project: Project,
    file: VirtualFile,
  ): Function<in FileEditor, out JComponent?> {
    val suggestionData = getSuggestionData(project, ApplicationInfo.getInstance().build.productCode, file.name, file.fileType)

    if (suggestionData == null) {
      ProcessIOExecutorService.INSTANCE.execute {
        val marketplaceRequests = MarketplaceRequests.getInstance()
        marketplaceRequests.loadJetBrainsPluginsIds()
        marketplaceRequests.loadExtensionsForIdes()

        val extensionsStateService = PluginAdvertiserExtensionsStateService.instance
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
        LOG.debug("Tried to update extensions cache for file '${file.name}'. shouldUpdateNotifications=$shouldUpdateNotifications")
      }

      return EditorNotificationProvider.CONST_NULL
    }

    return suggestionData
  }

  class AdvertiserSuggestion(
    private val project: Project,
    private val extensionOrFileName: String,
    dataSet: Set<PluginData>,
    jbPluginsIds: Set<String>,
    val suggestedIdes: List<SuggestedIde>,
  ) : Function<FileEditor, EditorNotificationPanel?> {

    private var disabledPlugin: IdeaPluginDescriptor? = null
    private val jbProduced = mutableSetOf<PluginId>()

    @VisibleForTesting
    val thirdParty = mutableSetOf<PluginId>()

    init {
      val descriptorsById = PluginManagerCore.buildPluginIdMap()
      for (data in dataSet) {
        val pluginId = data.pluginId

        val installedPlugin: IdeaPluginDescriptor? = descriptorsById[pluginId]
        if (installedPlugin != null) {
          if (!installedPlugin.isEnabled && disabledPlugin == null) {
            disabledPlugin = installedPlugin
          }
        }
        else if (!data.isBundled) {
          (if (jbPluginsIds.contains(pluginId.idString)) jbProduced else thirdParty) += pluginId
        }
      }
    }

    override fun apply(fileEditor: FileEditor): EditorNotificationPanel? {
      lateinit var label: JLabel
      val panel = object : EditorNotificationPanel(fileEditor) {
        init {
          label = myLabel
        }
      }

      val pluginAdvertiserExtensionsState = PluginAdvertiserExtensionsStateService.instance.createExtensionDataProvider(project)
      panel.text = IdeBundle.message("plugins.advertiser.plugins.found", extensionOrFileName)

      fun createInstallActionLabel(pluginIds: Set<PluginId>) {
        panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.install.plugins")) {
          FUSEventSource.EDITOR.logInstallPlugins(pluginIds.map { it.idString })
          installAndEnable(project, pluginIds, true) {
            pluginAdvertiserExtensionsState.addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName)
            updateAllNotifications(project)
          }
        }
      }

      if (disabledPlugin != null) {
        panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.enable.plugin", disabledPlugin!!.name)) {
          pluginAdvertiserExtensionsState.addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName)
          updateAllNotifications(project)
          FUSEventSource.EDITOR.logEnablePlugins(listOf(disabledPlugin!!.pluginId.idString), project)
          PluginManagerConfigurable.showPluginConfigurableAndEnable(project, setOf(disabledPlugin))
        }
      }
      else if (jbProduced.isNotEmpty()) {
        createInstallActionLabel(jbProduced)
      }
      else if (suggestedIdes.isNotEmpty()) {
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
        return panel    // Don't show the "Ignore extension" label
      }
      else if (thirdParty.isNotEmpty()) {
        createInstallActionLabel(thirdParty)
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
  }

  data class SuggestedIde(val name: String, val downloadUrl: String)

  companion object {

    private val LOG = logger<PluginAdvertiserEditorNotificationProvider>()

    @VisibleForTesting
    fun getSuggestionData(
      project: Project,
      activeProductCode: String,
      fileName: String,
      fileType: FileType,
    ): AdvertiserSuggestion? {
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
    ): AdvertiserSuggestion? {
      val marketplaceRequests = MarketplaceRequests.getInstance()
      val jbPluginsIds = marketplaceRequests.jetBrainsPluginsIds ?: return null
      val ideExtensions = marketplaceRequests.extensionsForIdes ?: return null

      val extensionOrFileName = extensionsData.extensionOrFileName
      val dataSet = extensionsData.plugins

      val hasBundledPlugin = getBundledPluginToInstall(dataSet).isNotEmpty()
      val suggestedIdes = if (fileType is PlainTextLikeFileType || fileType is DetectedByContentFileType) {
        getSuggestedIdes(activeProductCode, extensionOrFileName, ideExtensions).ifEmpty {
          if (hasBundledPlugin && !isIgnoreIdeSuggestion) listOf(ideaUltimate) else emptyList()
        }
      }
      else
        emptyList()

      return AdvertiserSuggestion(project, extensionOrFileName, dataSet, jbPluginsIds, suggestedIdes)
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