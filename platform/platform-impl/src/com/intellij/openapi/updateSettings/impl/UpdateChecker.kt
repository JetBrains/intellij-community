// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.IdeBundle
import com.intellij.ide.externalComponents.ExternalComponentManager
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.*
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.LogUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrameUpdater
import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.containers.MultiMap
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.URLUtil
import com.intellij.util.text.VersionComparatorUtil
import com.intellij.util.text.nullize
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import javax.swing.JComponent
import kotlin.collections.HashSet
import kotlin.collections.set

private val LOG = logger<UpdateChecker>()

private const val DISABLED_UPDATE = "disabled_update.txt"

private enum class NotificationUniqueType {
  PLATFORM, PLUGINS, EXTERNAL
}

/**
 * See XML file by [ApplicationInfoEx.getUpdateUrls] for reference.
 */
object UpdateChecker {
  private val notificationGroupRef by lazy {
    NotificationGroup("IDE and Plugin Updates", NotificationDisplayType.STICKY_BALLOON, true, null, null, null, PluginManagerCore.CORE_ID)
  }

  @JvmField
  @Deprecated(level = DeprecationLevel.ERROR, replaceWith = ReplaceWith("getNotificationGroup()"), message = "Use getNotificationGroup()")
  val NOTIFICATIONS = notificationGroupRef

  @JvmStatic
  fun getNotificationGroup() = notificationGroupRef

  private var ourDisabledToUpdatePlugins: MutableSet<PluginId>? = null
  private val ourAdditionalRequestOptions = hashMapOf<String, String>()
  private val ourUpdatedPlugins = hashMapOf<PluginId, PluginDownloader>()
  private val ourShownNotifications = MultiMap<NotificationUniqueType, Notification>()

  /**
   * Adding a plugin ID to this collection allows to exclude a plugin from a regular update check.
   * Has no effect on non-bundled plugins.
   */
  @Suppress("MemberVisibilityCanBePrivate")
  val excludedFromUpdateCheckPlugins: HashSet<String> = hashSetOf()

  private val updateUrl: String
    get() = System.getProperty("idea.updates.url") ?: ApplicationInfoEx.getInstanceEx().updateUrls!!.checkingUrl

  /**
   * For scheduled update checks.
   */
  @JvmStatic
  fun updateAndShowResult(): ActionCallback {
    val callback = ActionCallback()
    ApplicationManager.getApplication().executeOnPooledThread {
      doUpdateAndShowResult(null, true, false, false, UpdateSettings.getInstance(), null, callback)
    }
    return callback
  }

  /**
   * For manual update checks (Help | Check for Updates, Settings | Updates | Check Now)
   * (the latter action may pass customized update settings).
   */
  @JvmStatic
  fun updateAndShowResult(project: Project?, customSettings: UpdateSettings?) {
    val settings = customSettings ?: UpdateSettings.getInstance()
    val fromSettings = customSettings != null

    ProgressManager.getInstance().run(object : Task.Backgroundable(project, IdeBundle.message("updates.checking.progress"), true) {
      override fun run(indicator: ProgressIndicator) = doUpdateAndShowResult(getProject(), !fromSettings,
                                                                             fromSettings || WelcomeFrame.getInstance() != null, true,
                                                                             settings, indicator, null)

      override fun isConditionalModal(): Boolean = fromSettings
      override fun shouldStartInBackground(): Boolean = !fromSettings
    })
  }

  /**
   * An immediate check for plugin updates for use from a command line (read "Toolbox").
   */
  @JvmStatic
  fun getPluginUpdates(): Collection<PluginDownloader>? = checkPluginsUpdate(EmptyProgressIndicator()).availableUpdates

  private fun doUpdateAndShowResult(project: Project?,
                                    showSettingsLink: Boolean,
                                    showDialog: Boolean,
                                    showEmptyNotification: Boolean,
                                    updateSettings: UpdateSettings,
                                    indicator: ProgressIndicator?,
                                    callback: ActionCallback?) {
    // check platform update

    indicator?.text = IdeBundle.message("updates.checking.platform")

    val result = checkPlatformUpdate(updateSettings)
    if (result.state == UpdateStrategy.State.CONNECTION_ERROR) {
      val e = result.error
      if (e != null) LOG.debug(e)
      showErrorMessage(showDialog, IdeBundle.message("updates.error.connection.failed", e?.message ?: "internal error"))
      callback?.setRejected()
      return
    }

    // check plugins update (with regard to potential platform update)

    indicator?.text = IdeBundle.message("updates.checking.plugins")

    val buildNumber: BuildNumber? = result.newBuild?.apiVersion

    val checkPluginsUpdateResult: CheckPluginsUpdateResult
    val externalUpdates: Collection<ExternalUpdate>?
    try {
      checkPluginsUpdateResult = checkPluginsUpdate(indicator, buildNumber)
      externalUpdates = checkExternalUpdates(showDialog, updateSettings, indicator)
    }
    catch (e: IOException) {
      showErrorMessage(showDialog, IdeBundle.message("updates.error.connection.failed", e.message))
      callback?.setRejected()
      return
    }

    // show result

    UpdateSettings.getInstance().saveLastCheckedInfo()

    ApplicationManager.getApplication().invokeLater {
      showUpdateResult(project, result, checkPluginsUpdateResult, externalUpdates, showSettingsLink, showDialog, showEmptyNotification)
      callback?.setDone()
    }
  }

  private fun checkPlatformUpdate(settings: UpdateSettings): CheckForUpdateResult {
    val updateInfo = try {
      var updateUrl = Urls.newFromEncoded(updateUrl)
      if (updateUrl.scheme != URLUtil.FILE_PROTOCOL) {
        updateUrl = prepareUpdateCheckArgs(updateUrl)
      }
      LogUtil.debug(LOG, "load update xml (UPDATE_URL='%s')", updateUrl)
      HttpRequests.request(updateUrl).connect { UpdatesInfo(JDOMUtil.load(it.reader)) }
    }
    catch (e: JDOMException) {
      // corrupted content, don't bother telling user
      LOG.info(e)
      null
    }
    catch (e: Exception) {
      LOG.info(e)
      return CheckForUpdateResult(UpdateStrategy.State.CONNECTION_ERROR, e)
    }

    if (updateInfo == null || !settings.isPlatformUpdateEnabled) {
      return CheckForUpdateResult(UpdateStrategy.State.NOTHING_LOADED, null)
    }

    val strategy = UpdateStrategy(ApplicationInfo.getInstance().build, updateInfo, settings)
    return strategy.checkForUpdates()
  }

  @JvmStatic
  @Throws(IOException::class, JDOMException::class)
  fun getUpdatesInfo(): UpdatesInfo? {
    val updateUrl = Urls.newFromEncoded(updateUrl)
    return HttpRequests.request(updateUrl).connect { UpdatesInfo(JDOMUtil.load(it.reader)) }
  }

  /**
   * [availableUpdates] - new versions of plugins compatible with the specified build

   * [customRepositoryPlugins] - plugins from custom repositories for the specified build

   * [incompatiblePlugins] - plugins that would be incompatible and don't have updates with the specified build
   *
   * If build is not specified then current IDE version is used.
   */
  private data class CheckPluginsUpdateResult(
    val availableUpdates: Collection<PluginDownloader>?,
    val customRepositoryPlugins: Collection<IdeaPluginDescriptor>,
    val incompatiblePlugins: Collection<IdeaPluginDescriptor>?
  )

  private val EMPTY_CHECK_UPDATE_RESULT = CheckPluginsUpdateResult(null, emptyList(), null)

  /**
   * If [newBuildNumber] is null, returns new versions of plugins compatible with the current IDE version.
   *
   * If not null, returns new versions of plugins compatible with the specified build.
   */
  private fun checkPluginsUpdate(
    indicator: ProgressIndicator?,
    newBuildNumber: BuildNumber? = null
  ): CheckPluginsUpdateResult {
    val updateable = collectUpdateablePlugins()
    if (updateable.isEmpty()) return EMPTY_CHECK_UPDATE_RESULT

    val toUpdate = mutableMapOf<PluginId, PluginDownloader>()

    val latestCustomPluginsAsMap = HashMap<PluginId, IdeaPluginDescriptor>()
    val state = InstalledPluginsState.getInstance()
    for (host in RepositoryHelper.getPluginHosts()) {
      try {
        if (host == null && ApplicationInfoEx.getInstanceEx().usesJetBrainsPluginRepository()) {
          validateCompatibleUpdatesForCurrentPlugins(updateable, toUpdate, newBuildNumber, state, indicator)
        }
        else {
          val list = RepositoryHelper.loadPlugins(host, newBuildNumber, indicator)
          for (descriptor in list) {
            val id = descriptor.pluginId
            if (updateable.containsKey(id)) {
              updateable.remove(id)
              buildDownloaderAndPrepareToInstall(state, descriptor, newBuildNumber, toUpdate, indicator, host)
            }
            //collect latest plugins from custom repos
            val storedDescriptor = latestCustomPluginsAsMap[id]
            if (storedDescriptor == null || StringUtil.compareVersionNumbers(descriptor.version, storedDescriptor.version) > 0) {
              latestCustomPluginsAsMap[id] = descriptor
            }
          }
        }
      }
      catch (e: IOException) {
        LOG.debug(e)
        LOG.info("failed to load plugin descriptions from ${host ?: "default repository"}: ${e.message}")
      }
    }

    val incompatiblePlugins: MutableCollection<IdeaPluginDescriptor>? = getIncompatiblePlugins(newBuildNumber, updateable, toUpdate)

    return CheckPluginsUpdateResult(if (toUpdate.isEmpty()) null else toUpdate.values, latestCustomPluginsAsMap.values, incompatiblePlugins)
  }

  private fun getIncompatiblePlugins(
    newBuildNumber: BuildNumber?,
    updateable: MutableMap<PluginId, IdeaPluginDescriptor?>,
    toUpdate: MutableMap<PluginId, PluginDownloader>
  ): MutableCollection<IdeaPluginDescriptor>? {
    if (newBuildNumber == null) return null
    val incompatiblePlugins: MutableCollection<IdeaPluginDescriptor> = HashSet()
    for ((_, installedPlugin) in updateable) {
      // collect plugins that were not updated and would be incompatible with the new version
      if (installedPlugin != null && installedPlugin.isEnabled &&
          !toUpdate.containsKey(installedPlugin.pluginId) &&
          !PluginManagerCore.isCompatible(installedPlugin, newBuildNumber)) {
        incompatiblePlugins += installedPlugin
      }
    }
    return incompatiblePlugins
  }

  @JvmStatic
  fun updateDescriptorsForInstalledPlugins(state: InstalledPluginsState) {
    val updateable = collectUpdateablePlugins()
    if (updateable.isEmpty()) return
    validateCompatibleUpdatesForCurrentPlugins(updateable, mutableMapOf(), null, state, null)
  }

  /**
   * Use Plugin Repository API for checking and loading compatible updates for updateable plugins.
   * If current plugin version is out of date, schedule downloading of a newer version.
   */
  private fun validateCompatibleUpdatesForCurrentPlugins(
    updateable: MutableMap<PluginId, IdeaPluginDescriptor?>,
    toUpdate: MutableMap<PluginId, PluginDownloader>,
    buildNumber: BuildNumber?,
    state: InstalledPluginsState,
    indicator: ProgressIndicator?
  ) {
    val marketplacePluginIds = MarketplaceRequests.getInstance().getMarketplacePlugins(indicator)
    val idsToUpdate = updateable.map { it.key.idString }.filter { it in marketplacePluginIds }
    val updates = MarketplaceRequests.getInstance().getLastCompatiblePluginUpdate(idsToUpdate, buildNumber)
    for ((id, descriptor) in updateable) {
      val lastUpdate = updates.find { it.pluginId == id.idString } ?: continue
      val isOutdated = descriptor == null || VersionComparatorUtil.compare(lastUpdate.version, descriptor.version) > 0
      if (isOutdated) {
        val newDescriptor = try {
          MarketplaceRequests.getInstance().loadPluginDescriptor(id.idString, lastUpdate, indicator)
        }
        catch (e: HttpRequests.HttpStatusException) {
          if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) continue
          else throw e
        }
        buildDownloaderAndPrepareToInstall(state, newDescriptor, buildNumber, toUpdate, indicator, null)
      }
    }
    toUpdate.keys.forEach { updateable.remove(it) }
  }

  private fun buildDownloaderAndPrepareToInstall(
    state: InstalledPluginsState,
    descriptor: IdeaPluginDescriptor,
    buildNumber: BuildNumber?,
    toUpdate: MutableMap<PluginId, PluginDownloader>,
    indicator: ProgressIndicator?,
    host: String?
  ) {
    val downloader = PluginDownloader.createDownloader(descriptor, host, buildNumber)
    state.onDescriptorDownload(descriptor)
    checkAndPrepareToInstall(downloader, state, toUpdate, indicator)
  }

  /**
   * Returns a list of plugins that are currently installed or were installed in the previous installation from which
   * we're importing the settings. Null values are for once-installed plugins.
   */
  private fun collectUpdateablePlugins(): MutableMap<PluginId, IdeaPluginDescriptor?> {
    val updateable = mutableMapOf<PluginId, IdeaPluginDescriptor?>()

    updateable += PluginManagerCore.getPlugins().filter { !it.isBundled || it.allowBundledUpdate() }.associateBy { it.pluginId }

    val onceInstalled = PluginManager.getOnceInstalledIfExists()
    if (onceInstalled != null) {
      try {
        Files.readAllLines(onceInstalled)
          .asSequence()
          .map { line -> PluginId.getId(line.trim { it <= ' ' }) }
          .filter { it !in updateable }
          .forEach { updateable[it] = null }
      }
      catch (e: IOException) {
        LOG.error(onceInstalled.toString(), e)
      }

      //noinspection SSBasedInspection
      onceInstalled.toFile().deleteOnExit()
    }

    if (!ApplicationManager.getApplication().isInternal && excludedFromUpdateCheckPlugins.isNotEmpty()) {
      excludedFromUpdateCheckPlugins.forEach {
        val excluded = PluginId.getId(it)
        val plugin = updateable[excluded]
        if (plugin != null && plugin.isBundled) {
          updateable.remove(excluded)
        }
      }
    }

    return updateable
  }

  @VisibleForTesting
  fun checkExternalUpdates(manualCheck: Boolean,
                           updateSettings: UpdateSettings,
                           indicator: ProgressIndicator?): Collection<ExternalUpdate> {
    val result = arrayListOf<ExternalUpdate>()
    val manager = ExternalComponentManager.getInstance()
    indicator?.text = IdeBundle.message("updates.external.progress")

    for (source in manager.getEnabledComponentSources(updateSettings)) {
      indicator?.checkCanceled()
      if (source.name in updateSettings.enabledExternalUpdateSources) {
        try {
          val siteResult = source.getAvailableVersions(indicator, updateSettings)
            .filter { it.isUpdateFor(manager.findExistingComponentMatching(it, source)) }
          if (siteResult.isNotEmpty()) {
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

  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @Deprecated("Use `checkAndPrepareToInstall` without `incompatiblePlugins` parameter", level = DeprecationLevel.ERROR)
  @Throws(IOException::class)
  @JvmStatic
  fun checkAndPrepareToInstall(
    downloader: PluginDownloader,
    state: InstalledPluginsState,
    toUpdate: MutableMap<PluginId, PluginDownloader>,
    incompatiblePlugins: MutableCollection<IdeaPluginDescriptor>?,
    indicator: ProgressIndicator?
  ) {
    checkAndPrepareToInstall(downloader, state, toUpdate, indicator)

    val pluginId = downloader.id
    if (PluginManagerCore.isDisabled(pluginId)) return
    val installedPlugin = PluginManagerCore.getPlugin(pluginId)
    // collect plugins that were not updated and would be incompatible with the new version
    if (incompatiblePlugins != null && installedPlugin != null && installedPlugin.isEnabled &&
        !toUpdate.containsKey(installedPlugin.pluginId) &&
        !PluginManagerCore.isCompatible(installedPlugin, downloader.buildNumber)) {
      incompatiblePlugins += installedPlugin
    }
  }

  @JvmStatic
  fun mergePluginsFromRepositories(
    marketplaceUpdates: List<IdeaPluginDescriptor>,
    customPlugins: List<IdeaPluginDescriptor>
  ): List<IdeaPluginDescriptor> {
    val compatiblePluginMap = marketplaceUpdates.associateBy { it.pluginId }.toMutableMap()
    for (customPlugin in customPlugins) {
      val pluginId = customPlugin.pluginId
      val plugin = compatiblePluginMap[pluginId]
      if (plugin == null || PluginDownloader.compareVersionsSkipBrokenAndIncompatible(customPlugin.version, plugin) > 0) {
        compatiblePluginMap[pluginId] = customPlugin
      }
    }
    return compatiblePluginMap.values.toList()
  }

  @Throws(IOException::class)
  @JvmStatic
  fun checkAndPrepareToInstall(
    downloader: PluginDownloader,
    state: InstalledPluginsState,
    toUpdate: MutableMap<PluginId, PluginDownloader>,
    indicator: ProgressIndicator?
  ) {
    @Suppress("NAME_SHADOWING")
    var downloader = downloader
    val pluginId = downloader.id
    if (PluginManagerCore.isDisabled(pluginId)) return

    val pluginVersion = downloader.pluginVersion
    val installedPlugin = PluginManagerCore.getPlugin(pluginId)
    if (installedPlugin == null || pluginVersion == null || PluginDownloader.compareVersionsSkipBrokenAndIncompatible(pluginVersion, installedPlugin) > 0) {
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
        toUpdate[pluginId] = downloader
      }
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
                               checkPluginsUpdateResult: CheckPluginsUpdateResult,
                               externalUpdates: Collection<ExternalUpdate>?,
                               showSettingsLink: Boolean,
                               showDialog: Boolean,
                               showEmptyNotification: Boolean) {
    val updatedChannel = checkForUpdateResult.updatedChannel
    val newBuild = checkForUpdateResult.newBuild

    val updatedPlugins =
      checkPluginsUpdateResult.availableUpdates?.filter { downloader -> !PluginUpdateDialog.isIgnored(downloader.descriptor) }

    if (updatedChannel != null && newBuild != null) {
      val runnable = {
        UpdateInfoDialog(updatedChannel, newBuild, checkForUpdateResult.patches, showSettingsLink, updatedPlugins,
                         checkPluginsUpdateResult.incompatiblePlugins).show()
      }

      ourShownNotifications.remove(NotificationUniqueType.PLATFORM)?.forEach { it.expire() }

      if (showDialog) {
        runnable.invoke()
      }
      else {
        IdeUpdateUsageTriggerCollector.trigger("notification.shown")
        val title = IdeBundle.message("updates.new.build.notification.title", ApplicationNamesInfo.getInstance().fullProductName,
                                      newBuild.version)
        showNotification(project, title, "", {
          IdeUpdateUsageTriggerCollector.trigger("notification.clicked")
          runnable()
        }, null, NotificationUniqueType.PLATFORM, "ide.update.available")
      }
      return
    }

    var updateFound = false

    if (updatedPlugins != null && updatedPlugins.isNotEmpty()) {
      updateFound = true

      ourShownNotifications.remove(NotificationUniqueType.PLUGINS)?.forEach { it.expire() }

      if (showDialog || !canEnableNotifications()) {
        PluginUpdateDialog(updatedPlugins, checkPluginsUpdateResult.customRepositoryPlugins).show()
      }
      else {
        val runnable = { PluginManagerConfigurable.showPluginConfigurable(project, updatedPlugins) }

        val ideFrame = WelcomeFrame.getInstance()
        if (ideFrame is WelcomeFrameUpdater) {
          ideFrame.showPluginUpdates(runnable)
        }
        else {
          val names = updatedPlugins.joinToString { downloader -> StringUtil.wrapWithDoubleQuote(downloader.pluginName) }
          val title = if (updatedPlugins.size == 1) IdeBundle.message("updates.plugin.ready.short.title.available", names)
          else IdeBundle.message("updates.plugins.ready.short.title.available")
          val message = if (updatedPlugins.size == 1) "" else names
          showNotification(project, title, message, runnable, { notification ->
            notification.actions[0].templatePresentation.text = IdeBundle.message("plugin.settings.link.title")
            notification.actions.add(0, object : NotificationAction(
              IdeBundle.message(if (updatedPlugins.size == 1) "plugins.configurable.update.button" else "plugin.manager.update.all")) {
              override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                notification.expire()
                PluginUpdateDialog.runUpdateAll(updatedPlugins, e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as JComponent?)
              }
            })
            notification.addAction(object : NotificationAction(
              IdeBundle.message(if (updatedPlugins.size == 1) "updates.ignore.update.link" else "updates.ignore.updates.link")) {
              override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                notification.expire()
                PluginUpdateDialog.ignorePlugins(updatedPlugins.map { downloader -> downloader.descriptor })
              }
            })
          }, NotificationUniqueType.PLUGINS, "plugins.update.available")
        }
      }
    }

    if (externalUpdates != null && !externalUpdates.isEmpty()) {
      updateFound = true

      ourShownNotifications.remove(NotificationUniqueType.EXTERNAL)?.forEach { it.expire() }

      for (update in externalUpdates) {
        val runnable = { update.source.installUpdates(update.components) }

        if (showDialog) {
          runnable.invoke()
        }
        else {
          val title = IdeBundle.message("updates.plugins.ready.title.available", ApplicationNamesInfo.getInstance().fullProductName)
          val updates = update.components.joinToString(", ")
          val message = IdeBundle.message("updates.external.ready.message", update.components.size, updates)
          showNotification(project, title, message, runnable, null, NotificationUniqueType.EXTERNAL, "external.components.available")
        }
      }
    }

    if (!updateFound) {
      if (showDialog) {
        NoUpdatesDialog(showSettingsLink).show()
      }
      else if (showEmptyNotification) {
        ourShownNotifications.remove(NotificationUniqueType.PLUGINS)?.forEach { it.expire() }

        val title = IdeBundle.message("updates.no.updates.notification")
        showNotification(project, title, "", {}, { notification -> notification.actions.clear() }, NotificationUniqueType.PLUGINS, "no.updates.available")
      }
    }
  }

  private fun canEnableNotifications(): Boolean {
    if (WelcomeFrame.getInstance() is WelcomeFrameUpdater) {
      return true
    }
    return NotificationsConfigurationImpl.getInstanceImpl().SHOW_BALLOONS && NotificationsConfigurationImpl.getSettings(
      getNotificationGroup().displayId).displayType != NotificationDisplayType.NONE
  }

  private fun showNotification(project: Project?,
                               title: String,
                               message: String,
                               action: () -> Unit,
                               extraBuilder: ((Notification) -> Unit)?,
                               notificationType: NotificationUniqueType,
                               notificationDisplayId: String) {
    val notification = getNotificationGroup().createNotification(title, if (message.isEmpty()) "" else XmlStringUtil.wrapInHtml(message),
                                                                 NotificationType.INFORMATION, null, notificationDisplayId)
    notification.collapseActionsDirection = Notification.CollapseActionsDirection.KEEP_LEFTMOST
    notification.addAction(object : NotificationAction(IdeBundle.message("updates.notification.update.action")) {
      override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        notification.expire()
        action.invoke()
      }
    })
    extraBuilder?.invoke(notification)
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

  @Deprecated(message = "Use disabledToUpdate", replaceWith = ReplaceWith("disabledToUpdate"))
  @JvmStatic
  @Suppress("unused")
  val disabledToUpdatePlugins: Set<String>
    get() = disabledToUpdate.mapTo(TreeSet()) { it.idString }

  @JvmStatic
  val disabledToUpdate: Set<PluginId>
    get() {
      var result = ourDisabledToUpdatePlugins
      if (result == null) {
        result = TreeSet()
        if (!ApplicationManager.getApplication().isUnitTestMode) {
          try {
            val file = File(PathManager.getConfigPath(), DISABLED_UPDATE)
            if (file.isFile) {
              for (line in FileUtil.loadFile(file).split("[\\s]".toRegex())) {
                line.nullize(true)?.let {
                  result.add(PluginId.getId(it))
                }
              }
            }
          }
          catch (e: IOException) {
            LOG.error(e)
          }
        }

        ourDisabledToUpdatePlugins = result
      }
      return result
    }

  @JvmStatic
  fun saveDisabledToUpdatePlugins() {
    val plugins = Paths.get(PathManager.getConfigPath(), DISABLED_UPDATE)
    try {
      PluginManagerCore.savePluginsList(disabledToUpdate, plugins, false)
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
      if (app != null && !app.isDisposed && UpdateSettings.getInstance().isCheckNeeded) {
        val pluginDescriptor = PluginManagerCore.getPlugin(
          PluginUtil.getInstance().findPluginId(event.throwable))
        if (pluginDescriptor != null && !pluginDescriptor.isBundled) {
          ourHasFailedPlugins = true
          updateAndShowResult()
        }
      }
    }
  }

  /** A helper method for manually testing platform updates (see [com.intellij.internal.ShowUpdateInfoDialogAction]). */
  @ApiStatus.Internal
  fun testPlatformUpdate(project: Project?, updateInfoText: String, patchFilePath: String?, forceUpdate: Boolean) {
    if (!ApplicationManager.getApplication().isInternal) {
      throw IllegalStateException()
    }

    val channel: UpdateChannel?
    val newBuild: BuildInfo?
    val patches: UpdateChain?
    if (forceUpdate) {
      val node = JDOMUtil.load(updateInfoText).getChild("product")?.getChild("channel") ?: throw IllegalArgumentException(
        "//channel missing")
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
      UpdateInfoDialog(project, channel, newBuild, patches, patchFile).show()
    }
    else {
      NoUpdatesDialog(true).show()
    }
  }
}
