// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.ide.IdeBundle
import com.intellij.ide.externalComponents.ExternalComponentManager
import com.intellij.ide.externalComponents.ExternalComponentSource
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.notification.*
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.debug
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
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.reference.SoftReference
import com.intellij.util.Urls
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
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

private typealias PluginResults = Triple<PluginUpdates, Collection<PluginNode>, Map<String?, Exception>>
private typealias ExternalResults = Pair<Collection<ExternalUpdate>, Map<ExternalComponentSource, Exception>>

/**
 * See XML file by [ApplicationInfoEx.getUpdateUrls] for reference.
 */
object UpdateChecker {
  private val LOG = logger<UpdateChecker>()

  private const val DISABLED_UPDATE = "disabled_update.txt"
  private const val DISABLED_PLUGIN_UPDATE = "plugin_disabled_updates.txt"
  private const val PRODUCT_DATA_TTL_MS = 300_000L

  private enum class NotificationKind { PLATFORM, PLUGINS, EXTERNAL }

  private val updateUrl: String
    get() = System.getProperty("idea.updates.url") ?: ApplicationInfoEx.getInstanceEx().updateUrls!!.checkingUrl

  private val productDataLock = ReentrantLock()
  private var productDataCache: SoftReference<Result<Product?>>? = null
  private val ourUpdatedPlugins: MutableMap<PluginId, PluginDownloader> = HashMap()
  private val ourShownNotifications = MultiMap<NotificationKind, Notification>()

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
    ProcessIOExecutorService.INSTANCE.execute {
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
                                    preferDialog: Boolean,
                                    showSettingsLink: Boolean,
                                    indicator: ProgressIndicator?,
                                    callback: ActionCallback?) {
    indicator?.text = IdeBundle.message("updates.checking.platform")
    val platformUpdates = checkForPlatformUpdates(updateSettings, indicator)
    if (platformUpdates.state == UpdateStrategy.State.CONNECTION_ERROR) {
      if (userInitiated) {
        showErrors(project, IdeBundle.message("updates.error.connection.failed", platformUpdates.error?.message), preferDialog)
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
      showErrors(project, builder.wrapWithHtmlBody().toString(), preferDialog)
    }

    ApplicationManager.getApplication().invokeLater {
      showResults(project, platformUpdates, pluginUpdates, customRepoPlugins, externalUpdates, userInitiated, preferDialog, showSettingsLink)
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
  fun checkForPlatformUpdates(indicator: ProgressIndicator?): Triple<CheckForUpdateResult, List<PluginDownloader>?, Collection<IdeaPluginDescriptor>?> {
    indicator?.text = IdeBundle.message("updates.checking.platform")
    val platformUpdates = checkForPlatformUpdates(UpdateSettings.getInstance(), indicator)

    if (platformUpdates.state == UpdateStrategy.State.CONNECTION_ERROR || platformUpdates.state == UpdateStrategy.State.NOTHING_LOADED ||
        platformUpdates.updatedChannel == null || platformUpdates.newBuild == null) {
      return Triple(platformUpdates, null, null)
    }

    indicator?.text = IdeBundle.message("updates.checking.plugins")
    val (pluginUpdates, customRepoPlugins, _) = checkForPluginUpdates(platformUpdates.newBuild.apiVersion, indicator)
    val updatedPlugins = (pluginUpdates.enabled.asSequence() + pluginUpdates.disabled.asSequence()).filter { !isIgnored(it.descriptor) }.toList()

    return Triple(platformUpdates, updatedPlugins, customRepoPlugins)
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
        LOG.debug { "loading ${url}" }
        parseUpdateData(HttpRequests.request(url).connect { JDOMUtil.load(it.getReader(indicator)) })
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

  @ApiStatus.Internal
  @JvmStatic
  fun updateDescriptorsForInstalledPlugins(state: InstalledPluginsState) {
    if (ApplicationInfoEx.getInstanceEx().usesJetBrainsPluginRepository()) {
      val updateable = collectUpdateablePlugins()
      if (updateable.isNotEmpty()) {
        findUpdatesInJetBrainsRepository(updateable, mutableMapOf(), mutableMapOf(), null, state, null)
      }
    }
  }

  private fun checkForPluginUpdates(buildNumber: BuildNumber?, indicator: ProgressIndicator?): PluginResults {
    if (System.getProperty("idea.ignore.disabled.plugins") == null) {
      val brokenPlugins = MarketplaceRequests.Instance.getBrokenPlugins(ApplicationInfo.getInstance().build)
      if (brokenPlugins.isNotEmpty()) {
        PluginManagerCore.updateBrokenPlugins(brokenPlugins)
      }
    }

    val updateable = collectUpdateablePlugins()
    if (updateable.isEmpty()) {
      return PluginResults(PluginUpdates(emptyList(), emptyList(), emptyList()), emptyList(), emptyMap())
    }

    val toUpdate = HashMap<PluginId, PluginDownloader>()
    val toUpdateDisabled = HashMap<PluginId, PluginDownloader>()
    val customRepoPlugins = HashMap<PluginId, PluginNode>()
    val errors = LinkedHashMap<String?, Exception>()
    val state = InstalledPluginsState.getInstance()
    for (host in RepositoryHelper.getPluginHosts()) {
      try {
        if (host == null && ApplicationInfoEx.getInstanceEx().usesJetBrainsPluginRepository()) {
          findUpdatesInJetBrainsRepository(updateable, toUpdate, toUpdateDisabled, buildNumber, state, indicator)
        }
        else if (host != "__BUILTIN_PLUGINS_URL__") {
          RepositoryHelper.loadPlugins(host, buildNumber, indicator).forEach { descriptor ->
            val id = descriptor.pluginId
            if (updateable.remove(id) != null) {
              prepareDownloader(state, descriptor, buildNumber, toUpdate, toUpdateDisabled, indicator, host)
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
        LOG.info(
          "failed to load plugins from ${host ?: "default repository"}: ${e.message}",
          if (LOG.isDebugEnabled) e else null,
        )
        errors[host] = e
      }
    }

    val incompatible = if (buildNumber == null) emptyList() else {
      // collecting plugins that aren't going to be updated and are incompatible with the new build
      // (the map may contain updateable bundled plugins - those are expected to have a compatible version in IDE)
      updateable.values.asSequence()
        .filterNotNull()
        .filter { it.isEnabled && !it.isBundled && !PluginManagerCore.isCompatible(it, buildNumber) }
        .toSet()
    }

    return PluginResults(PluginUpdates(toUpdate.values, toUpdateDisabled.values, incompatible), customRepoPlugins.values, errors)
  }

  private fun collectUpdateablePlugins(): MutableMap<PluginId, IdeaPluginDescriptor?> {
    val updateable = HashMap<PluginId, IdeaPluginDescriptor?>()

    // installed plugins that could be updated (either downloaded or updateable bundled)
    PluginManagerCore.getPlugins()
      .filter { !it.isBundled || it.allowBundledUpdate() }
      .associateByTo(updateable) { it.pluginId }

    // plugins installed in an instance from which the settings were imported
    val onceInstalled = PluginManager.getOnceInstalledIfExists()
    if (onceInstalled != null) {
      try {
        Files.readAllLines(onceInstalled).forEach { line ->
          val id = PluginId.getId(line.trim { it <= ' ' })
          updateable.putIfAbsent(id, null)
        }
      }
      catch (e: IOException) {
        LOG.error(onceInstalled.toString(), e)
      }

      @Suppress("SSBasedInspection")
      onceInstalled.toFile().deleteOnExit()
    }

    // excluding plugins that take care about their own updates
    if (excludedFromUpdateCheckPlugins.isNotEmpty() && !ApplicationManager.getApplication().isInternal) {
      excludedFromUpdateCheckPlugins.forEach {
        val id = PluginId.getId(it)
        val plugin = updateable[id]
        if (plugin != null && plugin.isBundled) {
          updateable.remove(id)
        }
      }
    }

    return updateable
  }

  private fun findUpdatesInJetBrainsRepository(updateable: MutableMap<PluginId, IdeaPluginDescriptor?>,
                                               toUpdate: MutableMap<PluginId, PluginDownloader>,
                                               toUpdateDisabled: MutableMap<PluginId, PluginDownloader>,
                                               buildNumber: BuildNumber?,
                                               state: InstalledPluginsState,
                                               indicator: ProgressIndicator?) {
    val requests = MarketplaceRequests.Instance
    val marketplacePluginIds = requests.getMarketplacePlugins(indicator).map { it.idString }
    val idsToUpdate = updateable.keys.filter { it.idString in marketplacePluginIds }.toSet()
    val updates = requests.getLastCompatiblePluginUpdate(idsToUpdate, buildNumber)
    updateable.forEach { (id, descriptor) ->
      val lastUpdate = updates.find { it.pluginId == id.idString }
      if (lastUpdate != null &&
          (descriptor == null || PluginDownloader.compareVersionsSkipBrokenAndIncompatible(lastUpdate.version, descriptor, buildNumber) > 0)) {
        runCatching { requests.loadPluginDescriptor(id.idString, lastUpdate, indicator) }
          .onFailure { if (it !is HttpRequests.HttpStatusException || it.statusCode != HttpURLConnection.HTTP_NOT_FOUND) throw it }
          .onSuccess { prepareDownloader(state, it, buildNumber, toUpdate, toUpdateDisabled, indicator, null) }
      }
    }
    (toUpdate.keys.asSequence() + toUpdateDisabled.keys.asSequence()).forEach { updateable.remove(it) }
  }

  private fun prepareDownloader(state: InstalledPluginsState,
                                descriptor: PluginNode,
                                buildNumber: BuildNumber?,
                                toUpdate: MutableMap<PluginId, PluginDownloader>,
                                toUpdateDisabled: MutableMap<PluginId, PluginDownloader>,
                                indicator: ProgressIndicator?,
                                host: String?) {
    val downloader = PluginDownloader.createDownloader(descriptor, host, buildNumber)
    state.onDescriptorDownload(descriptor)
    checkAndPrepareToInstall(downloader, state, if (PluginManagerCore.isDisabled(downloader.id)) toUpdateDisabled else toUpdate, buildNumber, indicator)
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

  @Throws(IOException::class)
  @JvmOverloads
  @JvmStatic
  fun checkAndPrepareToInstall(
    originalDownloader: PluginDownloader,
    state: InstalledPluginsState,
    toUpdate: MutableMap<PluginId, PluginDownloader>,
    buildNumber: BuildNumber? = null,
    indicator: ProgressIndicator? = null,
  ) {
    val pluginId = originalDownloader.id
    val pluginVersion = originalDownloader.pluginVersion
    val installedPlugin = PluginManagerCore.getPlugin(pluginId)
    if (installedPlugin == null
        || pluginVersion == null
        || PluginDownloader.compareVersionsSkipBrokenAndIncompatible(pluginVersion, installedPlugin, buildNumber) > 0) {
      val oldDownloader = ourUpdatedPlugins[pluginId]
      val downloader = if (PluginManagerCore.isDisabled(pluginId)) {
        originalDownloader
      }
      else if (oldDownloader == null || StringUtil.compareVersionNumbers(pluginVersion, oldDownloader.pluginVersion) > 0) {
        val descriptor = originalDownloader.descriptor
        if (descriptor is PluginNode && descriptor.isIncomplete) {
          originalDownloader.prepareToInstall(indicator ?: EmptyProgressIndicator())
          ourUpdatedPlugins[pluginId] = originalDownloader
        }
        originalDownloader
      }
      else {
        oldDownloader
      }

      val descriptor = downloader.descriptor
      if (PluginManagerCore.isCompatible(descriptor, downloader.buildNumber) && !state.wasUpdated(descriptor.pluginId)) {
        toUpdate[pluginId] = downloader
      }
    }
  }

  private fun showErrors(project: Project?, @NlsContexts.DialogMessage message: String, preferDialog: Boolean) {
    if (preferDialog) {
      UIUtil.invokeLaterIfNeeded { Messages.showErrorDialog(project, message, IdeBundle.message("updates.error.connection.title")) }
    }
    else {
      getNotificationGroup().createNotification(message, NotificationType.WARNING).notify(project)
    }
  }

  @RequiresEdt
  private fun showResults(
    project: Project?,
    platformUpdates: CheckForUpdateResult,
    pluginUpdates: PluginUpdates,
    customRepoPlugins: Collection<PluginNode>,
    externalUpdates: Collection<ExternalUpdate>,
    userInitiated: Boolean,
    preferDialog: Boolean,
    showSettingsLink: Boolean,
  ) {
    val updatedChannel = platformUpdates.updatedChannel
    val newBuild = platformUpdates.newBuild
    val enabledPlugins = pluginUpdates.enabled.filter { !isIgnored(it.descriptor) }
    val updatedPlugins = (enabledPlugins.asSequence() + pluginUpdates.disabled.asSequence().filter { !isIgnored(it.descriptor) }).toList()
    val forceDialog = preferDialog || userInitiated && !notificationsEnabled()

    if (updatedChannel != null && newBuild != null) {
      val showNotification = userInitiated || WelcomeFrame.getInstance() != null

      if (showNotification) {
        ourShownNotifications.remove(NotificationKind.PLATFORM)?.forEach { it.expire() }
      }

      val runnable = {
        UpdateInfoDialog(
          project,
          updatedChannel,
          newBuild,
          platformUpdates.patches,
          showSettingsLink,
          updatedPlugins,
          pluginUpdates.incompatible,
        ).show()
      }

      if (forceDialog) {
        runnable()
      }
      else {
        UpdateSettingsEntryPointActionProvider.newPlatformUpdate(platformUpdates, updatedPlugins, pluginUpdates.incompatible)

        if (showNotification) {
          IdeUpdateUsageTriggerCollector.trigger("notification.shown")
          val message = IdeBundle.message("updates.new.build.notification.title", ApplicationNamesInfo.getInstance().fullProductName,
                                          newBuild.version)
          showNotification(
            project, NotificationKind.PLATFORM, "ide.update.available", "", message,
            NotificationAction.createSimpleExpiring(IdeBundle.message("updates.notification.update.action")) {
              IdeUpdateUsageTriggerCollector.trigger("notification.clicked")
              runnable()
            })
        }
      }

      return
    }

    if (enabledPlugins.isNotEmpty()) {
      if (userInitiated) {
        ourShownNotifications.remove(NotificationKind.PLUGINS)?.forEach { it.expire() }
      }

      val runnable = { PluginUpdateDialog(project, updatedPlugins, customRepoPlugins).show() }

      if (forceDialog) {
        runnable()
      }
      else {
        UpdateSettingsEntryPointActionProvider.newPluginUpdates(updatedPlugins, customRepoPlugins)

        if (userInitiated) {
          val (title, message) = when (updatedPlugins.size) {
            1 -> "" to IdeBundle.message("updates.plugin.ready.title", updatedPlugins[0].pluginName)
            else -> IdeBundle.message("updates.plugins.ready.title") to updatedPlugins.joinToString { "\"${it.pluginName}\"" }
          }
          showNotification(
            project, NotificationKind.PLUGINS, "plugins.update.available", title, message,
            NotificationAction.createExpiring(IdeBundle.message("updates.all.plugins.action", updatedPlugins.size)) { e, _ ->
              PluginUpdateDialog.runUpdateAll(updatedPlugins, e.getData(PlatformDataKeys.CONTEXT_COMPONENT) as JComponent?, null)
            },
            NotificationAction.createSimpleExpiring(IdeBundle.message("updates.plugins.dialog.action"), runnable),
            NotificationAction.createSimpleExpiring(IdeBundle.message("updates.ignore.updates.link", updatedPlugins.size)) {
              ignorePlugins(updatedPlugins.map { it.descriptor })
            })
        }
      }
    }

    if (externalUpdates.isNotEmpty()) {
      ourShownNotifications.remove(NotificationKind.EXTERNAL)?.forEach { it.expire() }

      for (update in externalUpdates) {
        val runnable = { update.source.installUpdates(update.components) }
        if (forceDialog) {
          runnable()
        }
        else {
          val message = IdeBundle.message("updates.external.ready.message", update.components.size, update.components.joinToString(", "))
          showNotification(
            project, NotificationKind.EXTERNAL, "external.components.available", "", message,
            NotificationAction.createSimpleExpiring(IdeBundle.message("updates.notification.update.action"), runnable))
        }
      }
    }

    if (enabledPlugins.isEmpty() && externalUpdates.isEmpty()) {
      if (forceDialog) {
        NoUpdatesDialog(showSettingsLink).show()
      }
      else if (userInitiated) {
        val message = IdeBundle.message("updates.no.updates.notification")
        showNotification(project, NotificationKind.PLUGINS,
                         "no.updates.available", "", message)
      }
    }
  }

  private fun notificationsEnabled(): Boolean =
    NotificationsConfigurationImpl.getInstanceImpl().SHOW_BALLOONS &&
    NotificationsConfigurationImpl.getSettings(getNotificationGroup().displayId).displayType != NotificationDisplayType.NONE

  private fun showNotification(project: Project?,
                               kind: NotificationKind,
                               displayId: String,
                               @NlsContexts.NotificationTitle title: String,
                               @NlsContexts.NotificationContent message: String,
                               vararg actions: NotificationAction) {
    val type = if (kind == NotificationKind.PLATFORM) NotificationType.IDE_UPDATE else NotificationType.INFORMATION
    val notification = getNotificationGroup().createNotification(title, XmlStringUtil.wrapInHtml(message), type, null, displayId)
    notification.collapseActionsDirection = Notification.CollapseActionsDirection.KEEP_LEFTMOST
    notification.whenExpired { ourShownNotifications.remove(kind, notification) }
    actions.forEach { notification.addAction(it) }
    notification.notify(project)
    ourShownNotifications.putValue(kind, notification)
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
    UpdateSettingsEntryPointActionProvider.removePluginsUpdate(descriptors.map { it.pluginId }.toSet())
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
        val pluginDescriptor = PluginManagerCore.getPlugin(PluginUtil.getInstance().findPluginId(event.throwable))
        if (pluginDescriptor != null && !pluginDescriptor.isBundled) {
          ourHasFailedPlugins = true
          updateAndShowResult()
        }
      }
    }
  }

  /** A helper method for manually testing platform updates (see [com.intellij.internal.ShowUpdateInfoDialogAction]). */
  @ApiStatus.Internal
  fun testPlatformUpdate(project: Project?, updateDataText: String, patchFilePath: String?, forceUpdate: Boolean) {
    if (!ApplicationManager.getApplication().isInternal) {
      throw IllegalStateException()
    }

    val channel: UpdateChannel?
    val newBuild: BuildInfo?
    val patches: UpdateChain?
    val currentBuild = ApplicationInfo.getInstance().build
    if (forceUpdate) {
      val node = JDOMUtil.load(updateDataText).getChild("product")?.getChild("channel") ?: throw IllegalArgumentException("//channel missing")
      channel = UpdateChannel(node, currentBuild.productCode)
      newBuild = channel.builds.firstOrNull() ?: throw IllegalArgumentException("//build missing")
      patches = newBuild.patches.firstOrNull()?.let { UpdateChain(listOf(it.fromBuild, newBuild.number), it.size) }
    }
    else {
      val product = parseUpdateData(JDOMUtil.load(updateDataText), currentBuild.productCode)
      val strategy = UpdateStrategy(currentBuild, product, UpdateSettings.getInstance())
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
