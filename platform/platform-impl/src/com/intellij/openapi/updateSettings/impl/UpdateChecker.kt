// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.diagnostic.IdeErrorsDialog
import com.intellij.externalDependencies.DependencyOnPlugin
import com.intellij.externalDependencies.ExternalDependenciesManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.externalComponents.ExternalComponentManager
import com.intellij.ide.plugins.*
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.*
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.LogUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.URLUtil
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil
import gnu.trove.THashMap
import org.jdom.JDOMException
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.collections.HashSet
import kotlin.collections.set

/**
 * See XML file by [ApplicationInfoEx.getUpdateUrls] for reference.
 */
object UpdateChecker {
  private val LOG = Logger.getInstance("#com.intellij.openapi.updateSettings.impl.UpdateChecker")

  @JvmField val NOTIFICATIONS: NotificationGroup = NotificationGroup(IdeBundle.message("update.notifications.title"), NotificationDisplayType.STICKY_BALLOON, true)

  private const val DISABLED_UPDATE = "disabled_update.txt"

  private enum class NotificationUniqueType { PLATFORM, PLUGINS, EXTERNAL }

  private var ourDisabledToUpdatePlugins: MutableSet<String>? = null
  private val ourAdditionalRequestOptions = THashMap<String, String>()
  private val ourUpdatedPlugins = hashMapOf<String, PluginDownloader>()
  private val ourShownNotifications = MultiMap<NotificationUniqueType, Notification>()

  /**
   * Adding a plugin ID to this collection allows to exclude a plugin from a regular update check.
   * Has no effect on non-bundled or "essential" (i.e. required for one of open projects) plugins.
   */
  @Suppress("MemberVisibilityCanBePrivate")
  val excludedFromUpdateCheckPlugins: HashSet<String> = hashSetOf()

  private val updateUrl: String
    get() = System.getProperty("idea.updates.url") ?: ApplicationInfoEx.getInstanceEx().updateUrls.checkingUrl

  /**
   * For scheduled update checks.
   */
  @JvmStatic
  fun updateAndShowResult(): ActionCallback {
    val callback = ActionCallback()
    ApplicationManager.getApplication().executeOnPooledThread {
      doUpdateAndShowResult(null, true, false, UpdateSettings.getInstance(), null, callback)
    }
    return callback
  }

  /**
   * For manual update checks (Help | Check for Updates, Settings | Updates | Check Now)
   * (the latter action may pass customised update settings).
   */
  @JvmStatic
  fun updateAndShowResult(project: Project?, customSettings: UpdateSettings?) {
    val settings = customSettings ?: UpdateSettings.getInstance()
    val fromSettings = customSettings != null

    ProgressManager.getInstance().run(object : Task.Backgroundable(project, IdeBundle.message("updates.checking.progress"), true) {
      override fun run(indicator: ProgressIndicator) = doUpdateAndShowResult(getProject(), fromSettings, true, settings, indicator, null)
      override fun isConditionalModal(): Boolean = fromSettings
      override fun shouldStartInBackground(): Boolean = !fromSettings
    })
  }

  /**
   * An immediate check for plugin updates for use from a command line (read "Toolbox").
   */
  @JvmStatic
  fun getPluginUpdates(): Collection<PluginDownloader>? =
    checkPluginsUpdate(UpdateSettings.getInstance(), EmptyProgressIndicator(), null, ApplicationInfo.getInstance().build)

  private fun doUpdateAndShowResult(project: Project?,
                                    fromSettings: Boolean,
                                    manualCheck: Boolean,
                                    updateSettings: UpdateSettings,
                                    indicator: ProgressIndicator?,
                                    callback: ActionCallback?) {
    // check platform update

    indicator?.text = IdeBundle.message("updates.checking.platform")

    val result = checkPlatformUpdate(updateSettings)
    if (result.state == UpdateStrategy.State.CONNECTION_ERROR) {
      val e = result.error
      if (e != null) LOG.debug(e)
      showErrorMessage(manualCheck, IdeBundle.message("updates.error.connection.failed", e?.message ?: "internal error"))
      callback?.setRejected()
      return
    }

    // check plugins update (with regard to potential platform update)

    indicator?.text = IdeBundle.message("updates.checking.plugins")

    val buildNumber: BuildNumber? = result.newBuild?.apiVersion
    val incompatiblePlugins: MutableCollection<IdeaPluginDescriptor>? = if (buildNumber != null) HashSet() else null

    val updatedPlugins: Collection<PluginDownloader>?
    val externalUpdates: Collection<ExternalUpdate>?
    try {
      updatedPlugins = checkPluginsUpdate(updateSettings, indicator, incompatiblePlugins, buildNumber)
      externalUpdates = checkExternalUpdates(manualCheck, updateSettings, indicator)
    }
    catch (e: IOException) {
      showErrorMessage(manualCheck, IdeBundle.message("updates.error.connection.failed", e.message))
      callback?.setRejected()
      return
    }

    // show result

    UpdateSettings.getInstance().saveLastCheckedInfo()

    ApplicationManager.getApplication().invokeLater({
      showUpdateResult(project, result, updateSettings, updatedPlugins, incompatiblePlugins, externalUpdates, !fromSettings, manualCheck)
      callback?.setDone()
    }, if (fromSettings) ModalityState.any() else ModalityState.NON_MODAL)
  }

  private fun checkPlatformUpdate(settings: UpdateSettings): CheckForUpdateResult {
    val updateInfo: UpdatesInfo?
    try {
      var updateUrl = Urls.newFromEncoded(updateUrl)
      if (updateUrl.scheme != URLUtil.FILE_PROTOCOL) {
        updateUrl = prepareUpdateCheckArgs(updateUrl)
      }
      LogUtil.debug(LOG, "load update xml (UPDATE_URL='%s')", updateUrl)

      updateInfo = HttpRequests.request(updateUrl)
          .forceHttps(settings.canUseSecureConnection())
          .connect {
            try {
              if (settings.isPlatformUpdateEnabled)
                UpdatesInfo(JDOMUtil.load(it.reader))
              else
                null
            }
            catch (e: JDOMException) {
              // corrupted content, don't bother telling user
              LOG.info(e)
              null
            }
          }
    }
    catch (e: Exception) {
      LOG.info(e)
      return CheckForUpdateResult(UpdateStrategy.State.CONNECTION_ERROR, e)
    }

    if (updateInfo == null) {
      return CheckForUpdateResult(UpdateStrategy.State.NOTHING_LOADED, null)
    }

    val strategy = UpdateStrategy(ApplicationInfo.getInstance().build, updateInfo, settings)
    return strategy.checkForUpdates()
  }

  @JvmStatic
  @Throws(IOException::class)
  fun getUpdatesInfo(settings: UpdateSettings): UpdatesInfo? {
    val updateUrl = Urls.newFromEncoded(updateUrl)
    LogUtil.debug(LOG, "load update xml (UPDATE_URL='%s')", updateUrl)

    return HttpRequests.request(updateUrl)
      .forceHttps(settings.canUseSecureConnection())
      .connect {
        try {
          UpdatesInfo(JDOMUtil.load(it.reader))
        }
        catch (e: JDOMException) {
          // corrupted content, don't bother telling user
          LOG.info(e)
          null
        }
      }
  }

  private fun checkPluginsUpdate(updateSettings: UpdateSettings,
                                 indicator: ProgressIndicator?,
                                 incompatiblePlugins: MutableCollection<IdeaPluginDescriptor>?,
                                 buildNumber: BuildNumber?): Collection<PluginDownloader>? {
    val updateable = collectUpdateablePlugins()
    if (updateable.isEmpty()) return null

    val toUpdate = ContainerUtil.newTroveMap<PluginId, PluginDownloader>()

    val state = InstalledPluginsState.getInstance()
    outer@ for (host in RepositoryHelper.getPluginHosts()) {
      try {
        val forceHttps = host == null && updateSettings.canUseSecureConnection()
        val list = RepositoryHelper.loadPlugins(host, buildNumber, forceHttps, indicator)
        for (descriptor in list) {
          val id = descriptor.pluginId
          if (updateable.containsKey(id)) {
            updateable.remove(id)
            state.onDescriptorDownload(descriptor)
            val downloader = PluginDownloader.createDownloader(descriptor, host, buildNumber, forceHttps)
            checkAndPrepareToInstall(downloader, state, toUpdate, incompatiblePlugins, indicator)
            if (updateable.isEmpty()) {
              break@outer
            }
          }
        }
      }
      catch (e: IOException) {
        LOG.debug(e)
        LOG.info("failed to load plugin descriptions from ${host ?: "default repository"}: ${e.message}")
      }
    }

    return if (toUpdate.isEmpty) null else toUpdate.values
  }

  /**
   * Returns a list of plugins which are currently installed or were installed in the previous installation from which
   * we're importing the settings.
   */
  private fun collectUpdateablePlugins(): MutableMap<PluginId, IdeaPluginDescriptor> {
    val updateable = ContainerUtil.newTroveMap<PluginId, IdeaPluginDescriptor>()

    updateable += PluginManagerCore.getPlugins().filter { !it.isBundled || it.allowBundledUpdate()}.associateBy { it.pluginId }

    val onceInstalled = PluginManager.getOnceInstalledIfExists()
    if (onceInstalled != null) {
      try {
        FileUtil.loadLines(onceInstalled)
          .map { line -> PluginId.getId(line.trim { it <= ' ' }) }
          .filter { it !in updateable }
          .forEach { updateable[it] = null }
      }
      catch (e: IOException) {
        LOG.error(onceInstalled.path, e)
      }

      //noinspection SSBasedInspection
      onceInstalled.deleteOnExit()
    }

    if (!excludedFromUpdateCheckPlugins.isEmpty()) {
      val required = ProjectManager.getInstance().openProjects
        .flatMap { ExternalDependenciesManager.getInstance(it).getDependencies(DependencyOnPlugin::class.java) }
        .map { PluginId.getId(it.pluginId) }
        .toSet()
      excludedFromUpdateCheckPlugins.forEach {
        val excluded = PluginId.getId(it)
        if (excluded !in required) {
          val plugin = updateable[excluded]
          if (plugin != null && plugin.isBundled) {
            updateable.remove(excluded)
          }
        }
      }
    }

    return updateable
  }

  private fun checkExternalUpdates(manualCheck: Boolean, updateSettings: UpdateSettings, indicator: ProgressIndicator?) : Collection<ExternalUpdate> {
    val result = arrayListOf<ExternalUpdate>()
    val manager = ExternalComponentManager.getInstance()
    indicator?.text = IdeBundle.message("updates.external.progress")

    for (source in manager.componentSources) {
      indicator?.checkCanceled()
      if (source.name in updateSettings.enabledExternalUpdateSources) {
        try {
          val siteResult = source.getAvailableVersions(indicator, updateSettings)
            .filter { it.isUpdateFor(manager.findExistingComponentMatching(it, source)) }
          if (!siteResult.isEmpty()) {
            result += ExternalUpdate(siteResult, source)
          }
        }
        catch (e: Exception) {
          LOG.warn(e)
          showErrorMessage(manualCheck, IdeBundle.message("updates.external.error.message", source.name, e.message ?: "internal error"))
        }
      }
    }

    return result
  }

  @Throws(IOException::class)
  @JvmStatic
  fun checkAndPrepareToInstall(downloader: PluginDownloader,
                               state: InstalledPluginsState,
                               toUpdate: MutableMap<PluginId, PluginDownloader>,
                               incompatiblePlugins: MutableCollection<IdeaPluginDescriptor>?,
                               indicator: ProgressIndicator?) {
    @Suppress("NAME_SHADOWING")
    var downloader = downloader
    val pluginId = downloader.pluginId
    if (PluginManagerCore.getDisabledPlugins().contains(pluginId)) return

    val pluginVersion = downloader.pluginVersion
    val installedPlugin = PluginManager.getPlugin(PluginId.getId(pluginId))
    if (installedPlugin == null || pluginVersion == null || PluginDownloader.compareVersionsSkipBrokenAndIncompatible(installedPlugin, pluginVersion) > 0) {
      var descriptor: IdeaPluginDescriptor?

      val oldDownloader = ourUpdatedPlugins[pluginId]
      if (oldDownloader == null || StringUtil.compareVersionNumbers(pluginVersion, oldDownloader.pluginVersion) > 0) {
        descriptor = downloader.descriptor
        if (descriptor is PluginNode && descriptor.isIncomplete) {
          if (downloader.prepareToInstall(indicator ?: EmptyProgressIndicator())) {
            descriptor = downloader.descriptor
          }
          ourUpdatedPlugins[pluginId] = downloader
        }
      }
      else {
        downloader = oldDownloader
        descriptor = oldDownloader.descriptor
      }

      if (PluginManagerCore.isCompatible(descriptor, downloader.buildNumber) && !state.wasUpdated(descriptor.pluginId)) {
        toUpdate[PluginId.getId(pluginId)] = downloader
      }
    }

    // collect plugins which were not updated and would be incompatible with new version
    if (incompatiblePlugins != null && installedPlugin != null && installedPlugin.isEnabled &&
        !toUpdate.containsKey(installedPlugin.pluginId) &&
        !PluginManagerCore.isCompatible(installedPlugin, downloader.buildNumber)) {
      incompatiblePlugins += installedPlugin
    }
  }

  private fun showErrorMessage(showDialog: Boolean, message: String) {
    LOG.info(message)
    if (showDialog) {
      UIUtil.invokeLaterIfNeeded { Messages.showErrorDialog(message, IdeBundle.message("updates.error.connection.title")) }
    }
  }

  private fun showUpdateResult(project: Project?,
                               checkForUpdateResult: CheckForUpdateResult,
                               updateSettings: UpdateSettings,
                               updatedPlugins: Collection<PluginDownloader>?,
                               incompatiblePlugins: Collection<IdeaPluginDescriptor>?,
                               externalUpdates: Collection<ExternalUpdate>?,
                               enableLink: Boolean,
                               alwaysShowResults: Boolean) {
    val updatedChannel = checkForUpdateResult.updatedChannel
    val newBuild = checkForUpdateResult.newBuild

    if (updatedChannel != null && newBuild != null) {
      val runnable = {
        val patches = checkForUpdateResult.patches
        val forceHttps = updateSettings.canUseSecureConnection()
        UpdateInfoDialog(updatedChannel, newBuild, patches, enableLink, forceHttps, updatedPlugins, incompatiblePlugins).show()
      }

      ourShownNotifications.remove(NotificationUniqueType.PLATFORM)?.forEach { it.expire() }

      if (alwaysShowResults) {
        runnable.invoke()
      }
      else {
        IdeUpdateUsageTriggerCollector.trigger("notification.shown")
        val message = IdeBundle.message("updates.ready.message", ApplicationNamesInfo.getInstance().fullProductName)
        showNotification(project, message, {
          IdeUpdateUsageTriggerCollector.trigger( "notification.clicked")
          runnable()
        }, NotificationUniqueType.PLATFORM)
      }
      return
    }

    var updateFound = false

    if (updatedPlugins != null && !updatedPlugins.isEmpty()) {
      updateFound = true
      val runnable = { PluginUpdateInfoDialog(updatedPlugins, enableLink).show() }

      ourShownNotifications.remove(NotificationUniqueType.PLUGINS)?.forEach { it.expire() }

      if (alwaysShowResults) {
        runnable.invoke()
      }
      else {
        val plugins = updatedPlugins.joinToString { downloader -> downloader.pluginName }
        val message = IdeBundle.message("updates.plugins.ready.message", updatedPlugins.size, plugins)
        showNotification(project, message, runnable, NotificationUniqueType.PLUGINS)
      }
    }

    if (externalUpdates != null && !externalUpdates.isEmpty()) {
      updateFound = true

      ourShownNotifications.remove(NotificationUniqueType.EXTERNAL)?.forEach { it.expire() }

      for (update in externalUpdates) {
        val runnable = { update.source.installUpdates(update.components) }

        if (alwaysShowResults) {
          runnable.invoke()
        }
        else {
          val updates = update.components.joinToString(", ")
          val message = IdeBundle.message("updates.external.ready.message", update.components.size, updates)
          showNotification(project, message, runnable, NotificationUniqueType.EXTERNAL)
        }
      }
    }

    if (!updateFound && alwaysShowResults) {
      NoUpdatesDialog(enableLink).show()
    }
  }

  private fun showNotification(project: Project?, message: String, action: () -> Unit, notificationType: NotificationUniqueType) {
    val listener = NotificationListener { notification, _ ->
      notification.expire()
      action.invoke()
    }

    val title = IdeBundle.message("update.notifications.title")
    val notification = NOTIFICATIONS.createNotification(title, XmlStringUtil.wrapInHtml(message), NotificationType.INFORMATION, listener)
    notification.whenExpired { ourShownNotifications.remove(notificationType, notification) }
    notification.notify(project)
    ourShownNotifications.putValue(notificationType, notification)
  }

  @JvmStatic
  fun addUpdateRequestParameter(name: String, value: String) {
    ourAdditionalRequestOptions[name] = value
  }

  private fun prepareUpdateCheckArgs(url: Url): Url {
    addUpdateRequestParameter("build", ApplicationInfo.getInstance().build.asString())
    addUpdateRequestParameter("uid", PermanentInstallationID.get())
    addUpdateRequestParameter("os", SystemInfo.OS_NAME + ' ' + SystemInfo.OS_VERSION)
    if (ExternalUpdateManager.ACTUAL != null) {
      addUpdateRequestParameter("manager", ExternalUpdateManager.ACTUAL.toolName)
    }
    if (ApplicationInfoEx.getInstanceEx().isEAP) {
      addUpdateRequestParameter("eap", "")
    }
    return url.addParameters(ourAdditionalRequestOptions)
  }

  @Deprecated("Replaced", ReplaceWith("PermanentInstallationID.get()", "com.intellij.openapi.application.PermanentInstallationID"))
  @JvmStatic
  @Suppress("unused", "UNUSED_PARAMETER")
  fun getInstallationUID(c: PropertiesComponent): String = PermanentInstallationID.get()

  @JvmStatic
  val disabledToUpdatePlugins: Set<String>
    get() {
      if (ourDisabledToUpdatePlugins == null) {
        ourDisabledToUpdatePlugins = TreeSet()
        if (!ApplicationManager.getApplication().isUnitTestMode) {
          try {
            val file = File(PathManager.getConfigPath(), DISABLED_UPDATE)
            if (file.isFile) {
              FileUtil.loadFile(file)
                  .split("[\\s]".toRegex())
                  .map { it.trim() }
                  .filterTo(ourDisabledToUpdatePlugins!!) { it.isNotEmpty() }
            }
          }
          catch (e: IOException) {
            LOG.error(e)
          }
        }
      }

      return ourDisabledToUpdatePlugins!!
    }

  @JvmStatic
  fun saveDisabledToUpdatePlugins() {
    val plugins = File(PathManager.getConfigPath(), DISABLED_UPDATE)
    try {
      PluginManagerCore.savePluginsList(disabledToUpdatePlugins, false, plugins)
    }
    catch (e: IOException) {
      LOG.error(e)
    }
  }

  private var ourHasFailedPlugins = false

  @JvmStatic
  fun checkForUpdate(event: IdeaLoggingEvent) {
    if (!ourHasFailedPlugins) {
      val app = ApplicationManager.getApplication()
      if (app != null && !app.isDisposed && !app.isDisposeInProgress && UpdateSettings.getInstance().isCheckNeeded) {
        val pluginDescriptor = PluginManager.getPlugin(IdeErrorsDialog.findPluginId(event.throwable))
        if (pluginDescriptor != null && !pluginDescriptor.isBundled) {
          ourHasFailedPlugins = true
          updateAndShowResult()
        }
      }
    }
  }

  /** A helper method for manually testing platform updates (see [com.intellij.internal.ShowUpdateInfoDialogAction]). */
  fun testPlatformUpdate(updateInfoText: String, patchFilePath: String?, forceUpdate: Boolean) {
    if (!ApplicationManager.getApplication().isInternal) {
      throw IllegalStateException()
    }

    val channel: UpdateChannel?
    val newBuild: BuildInfo?
    val patches: UpdateChain?
    if (forceUpdate) {
      val node = JDOMUtil.load(updateInfoText).getChild("product")?.getChild("channel") ?: throw IllegalArgumentException("//channel missing")
      channel = UpdateChannel(node)
      newBuild = channel.builds.firstOrNull() ?: throw IllegalArgumentException("//build missing")
      patches = newBuild.patches.firstOrNull()?.let { UpdateChain(listOf(it.fromBuild, newBuild.number), it.size) }
    }
    else {
      val updateInfo = UpdatesInfo(JDOMUtil.load(updateInfoText))
      val strategy = UpdateStrategy(ApplicationInfo.getInstance().build, updateInfo)
      val checkForUpdateResult = strategy.checkForUpdates()
      channel = checkForUpdateResult.updatedChannel
      newBuild = checkForUpdateResult.newBuild
      patches = checkForUpdateResult.patches
    }

    if (channel != null && newBuild != null) {
      val patchFile = if (patchFilePath != null) File(FileUtil.toSystemDependentName(patchFilePath)) else null
      UpdateInfoDialog(channel, newBuild, patches, patchFile).show()
    }
    else {
      NoUpdatesDialog(true).show()
    }
  }
}