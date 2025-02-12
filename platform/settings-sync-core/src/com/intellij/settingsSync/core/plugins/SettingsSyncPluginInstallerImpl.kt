package com.intellij.settingsSync.core.plugins

import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.currentOrDefaultProject
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.settingsSync.core.RestartForPluginInstall
import com.intellij.settingsSync.core.SettingsSyncBundle
import com.intellij.settingsSync.core.SettingsSyncEvents
import com.intellij.settingsSync.core.SettingsSyncSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal open class SettingsSyncPluginInstallerImpl(private val notifyErrors: Boolean) : SettingsSyncPluginInstaller {
  companion object {
    val LOG = logger<SettingsSyncPluginInstallerImpl>()
  }

  override suspend fun installPlugins(pluginsToInstall: List<PluginId>) {
    if (pluginsToInstall.isEmpty())
      return

    val project: Project? = ProjectManager.getInstanceIfCreated()?.openProjects?.firstOrNull()
    val downloaders = withBackgroundProgress(currentOrDefaultProject(project), SettingsSyncBundle.message("installing.plugins.indicator")) {
      createDownloaders(pluginsToInstall)
    }
    val remainingPluginIds = mutableSetOf(*pluginsToInstall.toTypedArray())
    downloaders.forEach {
      remainingPluginIds.remove(it.id)
    }
    var settingsChanged = false
    remainingPluginIds.forEach {
      LOG.info("Cannot find compatible updates for $it. Will not try to install it again.")
      disablePluginSync(it)
      settingsChanged = true
    }
    installCollected(downloaders, settingsChanged)
  }

  internal open suspend fun installCollected(installers: List<PluginDownloader>, settingsAlreadyChanged: Boolean) {
    withModalProgress(ModalTaskOwner.guess(), SettingsSyncBundle.message("installing.plugins.indicator"), TaskCancellation.nonCancellable()) {
      doInstallCollected(installers, settingsAlreadyChanged)
    }
  }

  internal suspend fun doInstallCollected(installers: List<PluginDownloader>, settingsAlreadyChanged: Boolean) {
    val pluginsRequiredRestart = mutableListOf<String>()
    var settingsChanged = settingsAlreadyChanged
    for (installer in installers) {
      try {
        if (!install(installer)) {
          pluginsRequiredRestart.add(installer.pluginName)
        }
        LOG.info("Setting sync installed plugin ID: ${installer.id.idString}")
      }
      catch (ex: Exception) {

        // currently, we don't install plugins that have missing dependencies.
        // TODO: toposort plugin with dependencies.
        // TODO: Skip installation dependent plugins, if any dependency fails to install.
        LOG.warn("An exception occurred while installing plugin ${installer.id.idString}. Will disable syncing this plugin", ex)
        disablePluginSync(installer.id)
        settingsChanged = true
      }
    }
    if (settingsChanged) {
      SettingsSyncEvents.getInstance().fireCategoriesChanged()
    }
    if (pluginsRequiredRestart.size > 0) {
      SettingsSyncEvents.getInstance().fireRestartRequired(RestartForPluginInstall(pluginsRequiredRestart))
    }
  }

  private fun disablePluginSync(pluginId: PluginId) {
    SettingsSyncSettings.getInstance().setSubcategoryEnabled(SettingsCategory.PLUGINS, pluginId.idString, false)
  }

  internal open suspend fun install(installer: PluginDownloader): Boolean {
    return withContext(Dispatchers.EDT) {
      installer.installDynamically(null)
    }
  }

  open internal fun createDownloaders(pluginIds: Collection<PluginId>): List<PluginDownloader> {
    val compatibleUpdates = MarketplaceRequests.getLastCompatiblePluginUpdate(pluginIds.toSet())
    val retval = arrayListOf<PluginDownloader>()
    for (update in compatibleUpdates) {
      val pluginDescriptor = MarketplaceRequests.loadPluginDescriptor(update.pluginId, update)
      val downloader = PluginDownloader.createDownloader(pluginDescriptor)
      if (downloader.prepareToInstall(null)) {
        retval.add(downloader)
      }
    }
    return retval
  }
}