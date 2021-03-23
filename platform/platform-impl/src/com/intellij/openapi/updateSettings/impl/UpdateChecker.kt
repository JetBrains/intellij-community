// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.externalComponents.ExternalComponentManager
import com.intellij.ide.externalComponents.ExternalComponentSource
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.*
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.reference.SoftReference
import com.intellij.util.Urls
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.URLUtil
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.swing.JComponent
import kotlin.Result
import kotlin.concurrent.withLock

private typealias PluginResults = Triple<PluginUpdates, Collection<IdeaPluginDescriptor>, Map<String?, Exception>>
private typealias ExternalResults = Pair<Collection<ExternalUpdate>, Map<ExternalComponentSource, Exception>>

/**
 * See XML file by [ApplicationInfoEx.getUpdateUrls] for reference.
 */
object UpdateChecker {
  private val LOG = logger<UpdateChecker>()

  private const val DISABLED_UPDATE = "disabled_update.txt"
  private const val DISABLED_PLUGIN_UPDATE = "plugin_disabled_updates.txt"
  private const val PRODUCT_DATA_TTL_MS = 300_000L

  private enum class NotificationUniqueType { PLATFORM, PLUGINS, EXTERNAL }

  private val updateUrl: String
    get() = System.getProperty("idea.updates.url") ?: ApplicationInfoEx.getInstanceEx().updateUrls!!.checkingUrl

  private val productDataLock = ReentrantLock()
  private var productDataCache: SoftReference<Result<Product?>>? = null
  private val ourUpdatedPlugins: MutableMap<PluginId, PluginDownloader> = HashMap()
  private val ourShownNotifications = MultiMap<NotificationUniqueType, Notification>()

  /**
   * Adding a plugin ID to this collection allows to exclude a plugin from a regular update check.
   * Has no effect on non-bundled plugins.
   */
  @Suppress("MemberVisibilityCanBePrivate")
  val excludedFromUpdateCheckPlugins: HashSet<String> = hashSetOf()

  init {
    UpdateRequestParameters.addParameter("build", ApplicationInfo.getInstance().build.asString())
    UpdateRequestParameters.addParameter("uid", PermanentInstallationID.get())
    UpdateRequestParameters.addParameter("os", SystemInfo.OS_NAME + ' ' + SystemInfo.OS_VERSION)
    if (ExternalUpdateManager.ACTUAL != null) {
      val name = if (ExternalUpdateManager.ACTUAL == ExternalUpdateManager.TOOLBOX) "Toolbox" else ExternalUpdateManager.ACTUAL.toolName
      UpdateRequestParameters.addParameter("manager", name)
    }
    if (ApplicationInfoEx.getInstanceEx().isEAP) {
      UpdateRequestParameters.addParameter("eap", "")
    }
  }

  @JvmStatic
  fun getNotificationGroup(): NotificationGroup =
    NotificationGroupManager.getInstance().getNotificationGroup("IDE and Plugin Updates")

  @JvmStatic
  fun getNotificationGroupForUpdateResults(): NotificationGroup =
    NotificationGroupManager.getInstance().getNotificationGroup("Plugin Update Results")

  /**
   * For scheduled update checks.
   */
  @JvmStatic
  fun updateAndShowResult(): ActionCallback {
    val callback = ActionCallback()
    ApplicationManager.getApplication().executeOnPooledThread {
      doUpdateAndShowResult(null, UpdateSettings.getInstance(), false, false, true, null, callback)
    }
    return callback
  }

  /**
   * For manual update checks (Help | Check for Updates, Settings | Updates | Check Now)
   * (the latter action passes customized update settings and forces results presentation in a dialog).
   */
  @JvmStatic
  fun updateAndShowResult(project: Project?, customSettings: UpdateSettings?) {
    val settings = customSettings ?: UpdateSettings.getInstance()
    val modal = customSettings != null
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, IdeBundle.message("updates.checking.progress"), true) {
      override fun run(indicator: ProgressIndicator) = doUpdateAndShowResult(getProject(), settings, true, modal, !modal, indicator, null)
      override fun isConditionalModal(): Boolean = modal
      override fun shouldStartInBackground(): Boolean = !modal
    })
  }

  private fun doUpdateAndShowResult(project: Project?,
                                    updateSettings: UpdateSettings,
                                    userInitiated: Boolean,
                                    forceDialog: Boolean,
                                    showSettingsLink: Boolean,
                                    indicator: ProgressIndicator?,
                                    callback: ActionCallback?) {
    indicator?.text = IdeBundle.message("updates.checking.platform")
    val platformUpdates = checkForPlatformUpdates(updateSettings, indicator)
    if (platformUpdates.state == UpdateStrategy.State.CONNECTION_ERROR) {
      if (userInitiated) {
        showErrors(project, IdeBundle.message("updates.error.connection.failed", platformUpdates.error?.message), forceDialog)
      }
      callback?.setRejected()
      return
    }

    indicator?.text = IdeBundle.message("updates.checking.plugins")
    val (pluginUpdates, customRepoPlugins, pluginErrors) = checkForPluginUpdates(platformUpdates.newBuild?.apiVersion, indicator)

    indicator?.text = IdeBundle.message("updates.external.progress")
    val (externalUpdates, externalErrors) = checkForExternalUpdates(updateSettings, indicator)

    UpdateSettings.getInstance().saveLastCheckedInfo()

    if (userInitiated && (pluginErrors.isNotEmpty() || externalErrors.isNotEmpty())) {
      val builder = HtmlBuilder()
      pluginErrors.forEach { (host, ex) ->
        if (!builder.isEmpty) builder.br()
        builder.append(
          if (host == null) IdeBundle.message("updates.plugins.error.message1", ex.message)
          else IdeBundle.message("updates.plugins.error.message2", host, ex.message))
      }
      externalErrors.forEach { (source, ex) ->
        if (!builder.isEmpty) builder.br()
        builder.append(IdeBundle.message("updates.external.error.message", source.name, ex.message))
      }
      showErrors(project, builder.wrapWithHtmlBody().toString(), forceDialog)
    }

    ApplicationManager.getApplication().invokeLater {
      showResults(project, platformUpdates, pluginUpdates, customRepoPlugins, externalUpdates, userInitiated, forceDialog, showSettingsLink)
      callback?.setDone()
    }
  }

  private fun checkForPlatformUpdates(settings: UpdateSettings, indicator: ProgressIndicator?): CheckForUpdateResult =
    try {
      val product = loadProductData(indicator)
      if (product != null) UpdateStrategy(ApplicationInfo.getInstance().build, product, settings).checkForUpdates()
      else CheckForUpdateResult(UpdateStrategy.State.NOTHING_LOADED, null)
    }
    catch (e: JDOMException) {
      LOG.infoWithDebug(e)
      CheckForUpdateResult(UpdateStrategy.State.NOTHING_LOADED, null)  // corrupted content, don't bother telling user
    }
    catch (e: Exception) {
      LOG.infoWithDebug(e)
      CheckForUpdateResult(UpdateStrategy.State.CONNECTION_ERROR, e)
    }

  @JvmStatic
  @Throws(IOException::class, JDOMException::class)
  fun loadProductData(indicator: ProgressIndicator?): Product? =
    productDataLock.withLock {
      val cached = SoftReference.dereference(productDataCache)
      if (cached != null) return@withLock cached.getOrThrow()

      val result = runCatching {
        var url = Urls.newFromEncoded(updateUrl)
        if (url.scheme != URLUtil.FILE_PROTOCOL) {
          url = UpdateRequestParameters.amendUpdateRequest(url)
        }
        if (LOG.isDebugEnabled) LOG.debug("loading ${url}")
        val updates = HttpRequests.request(url).connect { UpdatesInfo(JDOMUtil.load(it.getReader(indicator))) }
        updates[ApplicationInfo.getInstance().build.productCode]
      }

      productDataCache = SoftReference(result)
      AppExecutorUtil.getAppScheduledExecutorService().schedule(this::clearProductDataCache, PRODUCT_DATA_TTL_MS, TimeUnit.MILLISECONDS)
      return@withLock result.getOrThrow()
    }

  private fun clearProductDataCache() {
    if (productDataLock.tryLock(1, TimeUnit.MILLISECONDS)) {  // longer means loading now, no much sense in clearing
      productDataCache = null
      productDataLock.unlock()
    }
  }

  @JvmStatic
  fun findPluginUpdates(indicator: ProgressIndicator?): PluginUpdates = findPluginUpdates(null, indicator)

  /**
   * When [buildNumber] is null, returns new versions of plugins compatible with the current IDE version,
   * otherwise, returns versions compatible with the specified build.
   */
  @JvmStatic
  fun findPluginUpdates(buildNumber: BuildNumber?, indicator: ProgressIndicator?): PluginUpdates =
    checkForPluginUpdates(buildNumber, indicator).first

  private fun checkForPluginUpdates(buildNumber: BuildNumber?, indicator: ProgressIndicator?): PluginResults {
    if (System.getProperty("idea.ignore.disabled.plugins") == null) {
      val brokenPlugins = getBrokenPlugins()
      if (brokenPlugins.isNotEmpty()) {
        PluginManagerCore.updateBrokenPlugins(brokenPlugins)
      }
    }

    val updateable = collectUpdateablePlugins().ifEmpty {
      return PluginResults(PluginUpdates(emptyList(), emptyList(), emptyList()), emptyList(), emptyMap())
    }

    val toUpdate = HashMap<PluginId, PluginDownloader>()
    val toUpdateDisabled = HashMap<PluginId, PluginDownloader>()
    val customRepoPlugins = HashMap<PluginId, IdeaPluginDescriptor>()
    val errors = LinkedHashMap<String?, Exception>()
    val state = InstalledPluginsState.getInstance()
    for (host in RepositoryHelper.getPluginHosts()) {
      try {
        if (host == null && ApplicationInfoEx.getInstanceEx().usesJetBrainsPluginRepository()) {
          validateCompatibleUpdatesForCurrentPlugins(updateable, toUpdate, toUpdateDisabled, buildNumber, state, indicator)
        }
        else if (host != "__BUILTIN_PLUGINS_URL__") {
          val list = RepositoryHelper.loadPlugins(host, buildNumber, indicator)
          for (descriptor in list) {
            val id = descriptor.pluginId
            if (updateable.remove(id) != null) {
              buildDownloaderAndPrepareToInstall(state, descriptor, buildNumber, toUpdate, toUpdateDisabled, indicator, host)
            }
            // collect latest plugins from custom repos
            val storedDescriptor = customRepoPlugins[id]
            if (storedDescriptor == null || StringUtil.compareVersionNumbers(descriptor.version, storedDescriptor.version) > 0) {
              customRepoPlugins[id] = descriptor
            }
          }
        }
      }
      catch (e: Exception) {
        LOG.info("failed to load plugins from ${host ?: "default repository"}: ${e.message}", if (LOG.isDebugEnabled) e else null)
        errors[host] = e
      }
    }

    val incompatible = if (buildNumber == null) emptyList() else {
      // collect plugins that were not updated and would be incompatible with the new version
      // `updateable` could contain bundled plugins with allow-bundled-update - those always have compatible version in IDE
      updateable.values.asSequence()
        .filterNotNull()
        .filter { it.isEnabled && !it.isBundled && it.pluginId !in toUpdate.keys && !PluginManagerCore.isCompatible(it, buildNumber) }
        .toSet()
    }

    return PluginResults(PluginUpdates(toUpdate.values, toUpdateDisabled.values, incompatible), customRepoPlugins.values, errors)
  }

  private fun getBrokenPlugins(): Map<PluginId, Set<String>> {
    val currentBuild = ApplicationInfoImpl.getInstance().build
    val brokenPluginsMap = HashMap<PluginId, MutableSet<String>>()
    for (item in MarketplaceRequests.Instance.getBrokenPlugins()) {
      try {
        val parsedOriginalUntil = item.originalUntil?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        val parsedOriginalSince = item.originalSince?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        val originalUntil = BuildNumber.fromString(parsedOriginalUntil, item.id, null) ?: currentBuild
        val originalSince = BuildNumber.fromString(parsedOriginalSince, item.id, null) ?: currentBuild
        val until = BuildNumber.fromString(item.until) ?: currentBuild
        val since = BuildNumber.fromString(item.since) ?: currentBuild
        if (currentBuild !in originalSince..originalUntil || currentBuild in since..until) continue
        brokenPluginsMap.computeIfAbsent(PluginId.getId(item.id)) { HashSet() }.add(item.version)
      }
      catch (e: Exception) {
        LOG.error("cannot parse $item", e)
        continue
      }
    }
    return brokenPluginsMap
  }

  @JvmStatic
  fun updateDescriptorsForInstalledPlugins(state: InstalledPluginsState) {
    val updateable = collectUpdateablePlugins()
    if (updateable.isEmpty()) return
    validateCompatibleUpdatesForCurrentPlugins(updateable, mutableMapOf(), mutableMapOf(), null, state, null)
  }

  /**
   * Use Plugin Repository API for checking and loading compatible updates for updateable plugins.
   * If current plugin version is out of date, schedule downloading of a newer version.
   */
  private fun validateCompatibleUpdatesForCurrentPlugins(
    updateable: MutableMap<PluginId, IdeaPluginDescriptor?>,
    toUpdate: MutableMap<PluginId, PluginDownloader>,
    toUpdateDisabled: MutableMap<PluginId, PluginDownloader>,
    buildNumber: BuildNumber?,
    state: InstalledPluginsState,
    indicator: ProgressIndicator?
  ) {
    val requests = MarketplaceRequests.Instance
    val marketplacePluginIds = requests.getMarketplacePlugins(indicator).map { it.idString }
    val idsToUpdate = updateable.keys.filter { it.idString in marketplacePluginIds }.toSet()
    val updates = requests.getLastCompatiblePluginUpdate(idsToUpdate, buildNumber)
    for ((id, descriptor) in updateable) {
      val lastUpdate = updates.find { it.pluginId == id.idString } ?: continue
      val isOutdated = descriptor == null ||
                       PluginDownloader.compareVersionsSkipBrokenAndIncompatible(lastUpdate.version, descriptor, buildNumber) > 0
      if (isOutdated) {
        val newDescriptor = try {
          requests.loadPluginDescriptor(id.idString, lastUpdate, indicator)
        }
        catch (e: HttpRequests.HttpStatusException) {
          if (e.statusCode == HttpURLConnection.HTTP_NOT_FOUND) continue
          else throw e
        }
        buildDownloaderAndPrepareToInstall(state, newDescriptor, buildNumber, toUpdate, toUpdateDisabled, indicator, null)
      }
    }
    toUpdate.keys.forEach { updateable.remove(it) }
  }

  private fun buildDownloaderAndPrepareToInstall(
    state: InstalledPluginsState,
    descriptor: IdeaPluginDescriptor,
    buildNumber: BuildNumber?,
    toUpdate: MutableMap<PluginId, PluginDownloader>,
    toUpdateDisabled: MutableMap<PluginId, PluginDownloader>,
    indicator: ProgressIndicator?,
    host: String?
  ) {
    val downloader = PluginDownloader.createDownloader(descriptor, host, buildNumber)
    state.onDescriptorDownload(descriptor)
    checkAndPrepareToInstall(downloader, state, if (PluginManagerCore.isDisabled(downloader.id)) toUpdateDisabled else toUpdate, buildNumber, indicator)
  }

  /**
   * Returns a list of plugins that are currently installed or were installed in the previous installation from which
   * we're importing the settings. Null values are for once-installed plugins.
   */
  private fun collectUpdateablePlugins(): MutableMap<PluginId, IdeaPluginDescriptor?> {
    val updateable = HashMap<PluginId, IdeaPluginDescriptor?>()
    PluginManagerCore.getPlugins().filter { !it.isBundled || it.allowBundledUpdate() }.associateByTo(updateable) { it.pluginId }

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

      @Suppress("SSBasedInspection")
      onceInstalled.toFile().deleteOnExit()
    }

    if (excludedFromUpdateCheckPlugins.isNotEmpty() && !ApplicationManager.getApplication().isInternal) {
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

  @JvmStatic
  fun findExternalUpdates(updateSettings: UpdateSettings, indicator: ProgressIndicator?): Collection<ExternalUpdate> =
    checkForExternalUpdates(updateSettings, indicator).first

  private fun checkForExternalUpdates(updateSettings: UpdateSettings, indicator: ProgressIndicator?): ExternalResults {
    val result = ArrayList<ExternalUpdate>()
    val errors = LinkedHashMap<ExternalComponentSource, Exception>()

    val manager = ExternalComponentManager.getInstance()
    for (source in ExternalComponentManager.getComponentSources()) {
      indicator?.checkCanceled()
      try {
        val siteResult = source.getAvailableVersions(indicator, updateSettings)
          .filter { it.isUpdateFor(manager.findExistingComponentMatching(it, source)) }
        if (siteResult.isNotEmpty()) {
          result += ExternalUpdate(source, siteResult)
        }
      }
      catch (e: Exception) {
        LOG.info("failed to load updates for ${source}: ${e.message}", if (LOG.isDebugEnabled) e else null)
        errors[source] = e
      }
    }

    return ExternalResults(result, errors)
  }

  @JvmStatic
  fun mergePluginsFromRepositories(
    marketplaceUpdates: List<IdeaPluginDescriptor>,
    customPlugins: List<IdeaPluginDescriptor>,
    addNotExist: Boolean
  ): List<IdeaPluginDescriptor> {
    val compatiblePluginMap = marketplaceUpdates.associateBy { it.pluginId }.toMutableMap()
    for (customPlugin in customPlugins) {
      val pluginId = customPlugin.pluginId
      val plugin = compatiblePluginMap[pluginId]
      if (plugin == null) {
        if (addNotExist) {
          compatiblePluginMap[pluginId] = customPlugin
        }
      }
      else if (PluginDownloader.compareVersionsSkipBrokenAndIncompatible(customPlugin.version, plugin) > 0) {
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
    buildNumber: BuildNumber?,
    indicator: ProgressIndicator?
  ) {
    @Suppress("NAME_SHADOWING")
    var downloader = downloader
    val pluginId = downloader.id

    val pluginVersion = downloader.pluginVersion
    val installedPlugin = PluginManagerCore.getPlugin(pluginId)
    if (installedPlugin == null || pluginVersion == null ||
        PluginDownloader.compareVersionsSkipBrokenAndIncompatible(pluginVersion, installedPlugin, buildNumber) > 0) {
      var descriptor: IdeaPluginDescriptor?

      if (PluginManagerCore.isDisabled(pluginId)) {
        descriptor = downloader.descriptor
      }
      else {
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
      }

      if (PluginManagerCore.isCompatible(descriptor, downloader.buildNumber) && !state.wasUpdated(descriptor.pluginId)) {
        toUpdate[pluginId] = downloader
      }
    }
  }

  private fun showErrors(project: Project?, @NlsContexts.DialogMessage message: String, showDialog: Boolean) {
    if (showDialog) {
      UIUtil.invokeLaterIfNeeded { Messages.showErrorDialog(project, message, IdeBundle.message("updates.error.connection.title")) }
    }
    else {
      getNotificationGroup().createNotification(message, NotificationType.WARNING).notify(project)
    }
  }

  private fun showResults(project: Project?,
                          platformUpdates: CheckForUpdateResult,
                          pluginUpdates: PluginUpdates,
                          customRepoPlugins: Collection<IdeaPluginDescriptor>,
                          externalUpdates: Collection<ExternalUpdate>,
                          userInitiated: Boolean,
                          forceDialog: Boolean,
                          showSettingsLink: Boolean) {
    val updatedChannel = platformUpdates.updatedChannel
    val newBuild = platformUpdates.newBuild
    val updatedPlugins = (pluginUpdates.enabled.asSequence() + pluginUpdates.disabled.asSequence())
      .filter { !isIgnored(it.descriptor) }
      .toList()

    if (updatedChannel != null && newBuild != null) {
      val runnable = {
        UpdateInfoDialog(
          project, updatedChannel, newBuild, platformUpdates.patches, showSettingsLink, updatedPlugins, pluginUpdates.incompatible
        ).show()
      }

      ourShownNotifications.remove(NotificationUniqueType.PLATFORM)?.forEach { it.expire() }

      if (forceDialog) {
        runnable()
      }
      else {
        UpdateSettingsEntryPointActionProvider.newPlatformUpdate(platformUpdates, updatedPlugins, pluginUpdates.incompatible)

        IdeUpdateUsageTriggerCollector.trigger("notification.shown")
        if (userInitiated) {
          val title = IdeBundle.message("updates.new.build.notification.title", ApplicationNamesInfo.getInstance().fullProductName, newBuild.version)
          showNotification(project, title, "", {
            IdeUpdateUsageTriggerCollector.trigger("notification.clicked")
            runnable()
          }, null, NotificationUniqueType.PLATFORM, "ide.update.available")
        }
      }

      return
    }

    if (updatedPlugins.isNotEmpty()) {
      ourShownNotifications.remove(NotificationUniqueType.PLUGINS)?.forEach { it.expire() }

      val runnable = { PluginUpdateDialog(project, updatedPlugins, customRepoPlugins).show() }

      if (forceDialog || !canEnableNotifications()) {
        runnable()
      }
      // don't show notification if all updated plugins is disabled
      else if (updatedPlugins.size != updatedPlugins.count { downloader -> PluginManagerCore.isDisabled(downloader.id) }) {
        UpdateSettingsEntryPointActionProvider.newPluginUpdates(updatedPlugins, customRepoPlugins)

        if (userInitiated) {
          val names = updatedPlugins.joinToString { downloader -> StringUtil.wrapWithDoubleQuote(downloader.pluginName) }
          val title = if (updatedPlugins.size == 1) IdeBundle.message("updates.plugin.ready.short.title.available", names)
          else IdeBundle.message("updates.plugins.ready.short.title.available")
          val message = if (updatedPlugins.size == 1) "" else names

          showNotification(project, title, message, runnable, { notification ->
            notification.actions[0].templatePresentation.text = IdeBundle.message("plugin.settings.link.title")
            val text = if (updatedPlugins.size == 1) IdeBundle.message("plugins.configurable.update.button")
            else IdeBundle.message("plugin.manager.update.all")
            notification.actions.add(0, object : NotificationAction(text) {
              override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                notification.expire()
                PluginUpdateDialog.runUpdateAll(updatedPlugins, e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as JComponent?, null)
              }
            })
            notification.addAction(object : NotificationAction(IdeBundle.message("updates.ignore.updates.link", updatedPlugins.size)) {
              override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                notification.expire()
                ignorePlugins(updatedPlugins.map { it.descriptor })
              }
            })
          }, NotificationUniqueType.PLUGINS, "plugins.update.available")
        }
      }
    }

    if (externalUpdates.isNotEmpty()) {
      ourShownNotifications.remove(NotificationUniqueType.EXTERNAL)?.forEach { it.expire() }

      for (update in externalUpdates) {
        val runnable = { update.source.installUpdates(update.components) }
        if (forceDialog) {
          runnable()
        }
        else {
          val title = IdeBundle.message("updates.plugins.ready.title.available", ApplicationNamesInfo.getInstance().fullProductName)
          val updates = update.components.joinToString(", ")
          val message = IdeBundle.message("updates.external.ready.message", update.components.size, updates)
          showNotification(project, title, message, runnable, null, NotificationUniqueType.EXTERNAL, "external.components.available")
        }
      }
    }

    if (updatedPlugins.isEmpty() && externalUpdates.isEmpty()) {
      if (forceDialog) {
        NoUpdatesDialog(showSettingsLink).show()
      }
      else if (userInitiated) {
        val title = IdeBundle.message("updates.no.updates.notification")
        showNotification(project, title, "", {}, { notification -> notification.actions.clear() }, NotificationUniqueType.PLUGINS, "no.updates.available")
      }
    }
  }

  private fun canEnableNotifications(): Boolean =
    NotificationsConfigurationImpl.getInstanceImpl().SHOW_BALLOONS &&
    NotificationsConfigurationImpl.getSettings(getNotificationGroup().displayId).displayType != NotificationDisplayType.NONE

  private fun showNotification(project: Project?,
                               @NlsContexts.NotificationTitle title: String,
                               @NlsContexts.NotificationContent message: String,
                               action: () -> Unit,
                               extraBuilder: ((Notification) -> Unit)?,
                               notificationType: NotificationUniqueType,
                               notificationDisplayId: String) {
    val content = if (message.isEmpty()) "" else XmlStringUtil.wrapInHtml(message)
    val type = if (notificationType == NotificationUniqueType.PLATFORM) NotificationType.IDE_UPDATE else NotificationType.INFORMATION
    val notification = getNotificationGroup().createNotification(title, content, type, null, notificationDisplayId)
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
  val disabledToUpdate: Set<PluginId> by lazy { TreeSet(readConfigLines(DISABLED_UPDATE).map { PluginId.getId(it) }) }

  @JvmStatic
  fun saveDisabledToUpdatePlugins() {
    runCatching { DisabledPluginsState.savePluginsList(disabledToUpdate, Path.of(PathManager.getConfigPath(), DISABLED_UPDATE)) }
      .onFailure { LOG.error(it) }
  }

  @JvmStatic
  fun isIgnored(descriptor: IdeaPluginDescriptor): Boolean =
    descriptor.ignoredKey in ignoredPlugins

  @JvmStatic
  @JvmName("ignorePlugins")
  internal fun ignorePlugins(descriptors: List<IdeaPluginDescriptor>) {
    ignoredPlugins += descriptors.map { it.ignoredKey }
    runCatching { Files.write(Path.of(PathManager.getConfigPath(), DISABLED_PLUGIN_UPDATE), ignoredPlugins) }
      .onFailure { LOG.error(it) }
    UpdateSettingsEntryPointActionProvider.removePluginsUpdate(descriptors)
  }

  private val ignoredPlugins: MutableSet<String> by lazy { TreeSet(readConfigLines(DISABLED_PLUGIN_UPDATE)) }

  private val IdeaPluginDescriptor.ignoredKey: String
    get() = "${pluginId.idString}+${version}"

  private fun readConfigLines(fileName: String): List<String> {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      runCatching {
        val file = Path.of(PathManager.getConfigPath(), fileName)
        if (Files.isRegularFile(file)) {
          return Files.readAllLines(file)
        }
      }.onFailure { LOG.error(it) }
    }
    return emptyList()
  }

  private var ourHasFailedPlugins = false

  @JvmStatic
  fun checkForUpdate(event: IdeaLoggingEvent) {
    if (!ourHasFailedPlugins) {
      val app = ApplicationManager.getApplication()
      if (app != null && !app.isDisposed && UpdateSettings.getInstance().isPluginsCheckNeeded) {
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
      val node = JDOMUtil.load(updateInfoText).getChild("product")?.getChild("channel") ?: throw IllegalArgumentException("//channel missing")
      channel = UpdateChannel(node)
      newBuild = channel.builds.firstOrNull() ?: throw IllegalArgumentException("//build missing")
      patches = newBuild.patches.firstOrNull()?.let { UpdateChain(listOf(it.fromBuild, newBuild.number), it.size) }
    }
    else {
      val updateInfo = UpdatesInfo(JDOMUtil.load(updateInfoText))
      val strategy = UpdateStrategy(ApplicationInfo.getInstance().build, updateInfo, UpdateSettings.getInstance())
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

  //<editor-fold desc="Deprecated stuff.">
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  @Deprecated(level = DeprecationLevel.ERROR, replaceWith = ReplaceWith("getNotificationGroup()"), message = "Use getNotificationGroup()")
  @Suppress("DEPRECATION")
  @JvmField val NOTIFICATIONS =
    NotificationGroup("IDE and Plugin Updates", NotificationDisplayType.STICKY_BALLOON, true, null, null, null, PluginManagerCore.CORE_ID)

  @get:ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  @get:Deprecated(message = "Use disabledToUpdate", replaceWith = ReplaceWith("disabledToUpdate"))
  @Deprecated(message = "Use disabledToUpdate", replaceWith = ReplaceWith("disabledToUpdate"))
  @JvmStatic
  val disabledToUpdatePlugins: Set<String>
    get() = disabledToUpdate.mapTo(TreeSet()) { it.idString }

  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  @Deprecated(message = "Use checkForPluginUpdates", replaceWith = ReplaceWith(""))
  @JvmStatic
  fun getPluginUpdates(): Collection<PluginDownloader>? =
    findPluginUpdates(null, null).enabled.ifEmpty { null }
  //</editor-fold>
}
