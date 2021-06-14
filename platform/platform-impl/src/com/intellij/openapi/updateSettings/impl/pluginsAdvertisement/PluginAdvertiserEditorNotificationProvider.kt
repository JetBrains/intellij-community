// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications

class PluginAdvertiserEditorNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>(), DumbAware {
  override fun getKey(): Key<EditorNotificationPanel> = KEY

  override fun createNotificationPanel(file: VirtualFile,
                                       fileEditor: FileEditor,
                                       project: Project): EditorNotificationPanel? {
    val extensionsStateService = PluginAdvertiserExtensionsStateService.instance
    val pluginAdvertiserExtensionsState = extensionsStateService.createExtensionDataProvider(project)
    val extensionsData = pluginAdvertiserExtensionsState.requestExtensionData(file)
    val jbPluginsIds = MarketplaceRequests.Instance.jetBrainsPluginsIds

    if (extensionsData == null || jbPluginsIds == null) {
      ProcessIOExecutorService.INSTANCE.execute {
        MarketplaceRequests.Instance.loadJetBrainsPluginsIds()
        var shouldUpdateNotifications = extensionsStateService.updateCache(file.name)
        val fullExtension = PluginAdvertiserExtensionsStateService.getFullExtension(file)
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

    val extensionOrFileName = extensionsData.extensionOrFileName
    val dataSet = extensionsData.plugins
    val panel = EditorNotificationPanel(fileEditor)
    panel.text = IdeBundle.message("plugins.advertiser.plugins.found", extensionOrFileName)
    val onPluginsInstalled = Runnable {
      pluginAdvertiserExtensionsState.addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName)
      updateAllNotifications(project)
    }

    val pluginsToInstall = PluginsToInstall(dataSet, jbPluginsIds)
    val disabledPlugin = pluginsToInstall.myDisabledPlugin
    if (disabledPlugin != null) {
      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.enable.plugin", disabledPlugin.name)) {
        pluginAdvertiserExtensionsState.addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName)
        updateAllNotifications(project)
        FUSEventSource.EDITOR.logEnablePlugins(listOf(disabledPlugin.pluginId.idString), project)
        PluginManagerConfigurable.showPluginConfigurableAndEnable(project, setOf(disabledPlugin))
      }
    }
    else if (pluginsToInstall.myJbProduced.isNotEmpty()) {
      createInstallActionLabel(panel, pluginsToInstall.myJbProduced, onPluginsInstalled)
    }
    else if (!getBundledPluginToInstall(dataSet).isEmpty()) {
      if (isIgnoreUltimate) {
        return null
      }
      panel.text = IdeBundle.message("plugins.advertiser.extensions.supported.in.ultimate", extensionOrFileName)
      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.try.ultimate")) {
        pluginAdvertiserExtensionsState.addEnabledExtensionOrFileNameAndInvalidateCache(extensionOrFileName)
        FUSEventSource.EDITOR.openDownloadPageAndLog(project)
      }
      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.ignore.ultimate")) {
        FUSEventSource.EDITOR.doIgnoreUltimateAndLog(project)
        updateAllNotifications(project)
      }
    }
    else if (!pluginsToInstall.myThirdParty.isEmpty()) {
      createInstallActionLabel(panel, pluginsToInstall.myThirdParty, onPluginsInstalled)
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

  private class PluginsToInstall(dataSet: Set<PluginData>,
                                 jbPluginsIds: Set<String>) {
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

    private fun createInstallActionLabel(panel: EditorNotificationPanel,
                                         dataSet: Set<PluginData>,
                                         onSuccess: Runnable) {
      val pluginIds = dataSet.mapTo(mutableSetOf(), PluginData::pluginId)
      panel.createActionLabel(IdeBundle.message("plugins.advertiser.action.install.plugins")) {
        FUSEventSource.EDITOR.logInstallPlugins(pluginIds.map { it.idString })
        installAndEnable(pluginIds, onSuccess)
      }
    }

    private fun updateAllNotifications(project: Project) {
      EditorNotifications.getInstance(project).updateAllNotifications()
    }
  }
}