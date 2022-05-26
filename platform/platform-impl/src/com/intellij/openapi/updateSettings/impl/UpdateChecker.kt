// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.ide.IdeBundle
import com.intellij.ide.externalComponents.ExternalComponentManager
import com.intellij.ide.externalComponents.ExternalComponentSource
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import com.intellij.notification.*
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
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
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.reference.SoftReference
import com.intellij.util.Urls
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
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

/**
 * See XML file by [ApplicationInfoEx.getUpdateUrls] for reference.
 */
object UpdateChecker {
  private val LOG = logger<UpdateChecker>()

  private const val DISABLED_UPDATE = "disabled_update.txt"
  private const val DISABLED_PLUGIN_UPDATE = "plugin_disabled_updates.txt"
  private const val PRODUCT_DATA_TTL_MIN = 5L
  private const val MACHINE_ID_DISABLED_PROPERTY = "machine.id.disabled"
  private const val MACHINE_ID_PARAMETER = "mid"

  private enum class NotificationKind { PLATFORM, PLUGINS, EXTERNAL }

  private val updateUrl: String
    get() = System.getProperty("idea.updates.url") ?: ApplicationInfoEx.getInstanceEx().updateUrls!!.checkingUrl

  private val productDataLock = ReentrantLock()
  private var productDataCache: SoftReference<Result<Product?>>? = null
  private val ourUpdatedPlugins: MutableMap<PluginId, PluginDownloader> = HashMap()
  private val ourShownNotifications = MultiMap<NotificationKind, Notification>()
  private var machineIdInitialized = false

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
  fun getNotificationGroupForPluginUpdateResults(): NotificationGroup =
    NotificationGroupManager.getInstance().getNotificationGroup("Plugin Update Results")

  @JvmStatic
  fun getNotificationGroupForIdeUpdateResults(): NotificationGroup =
    NotificationGroupManager.getInstance().getNotificationGroup("IDE Update Results")

  /**
   * For scheduled update checks.
   */
  @JvmStatic
  fun updateAndShowResult(): ActionCallback {
    return ActionCallback().also {
      ProcessIOExecutorService.INSTANCE.execute {
        doUpdateAndShowResult(
          userInitiated = false,
          preferDialog = false,
          showSettingsLink = true,
          callback = it,
        )
      }
    }
  }

  /**
   * For manual update checks (Help | Check for Updates, Settings | Updates | Check Now)
   * (the latter action passes customized update settings and forces results presentation in a dialog).
   */
  @JvmStatic
  @JvmOverloads
  fun updateAndShowResult(
    project: Project?,
    customSettings: UpdateSettings? = null,
  ) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, IdeBundle.message("updates.checking.progress"), true) {
      override fun run(indicator: ProgressIndicator) = doUpdateAndShowResult(
        getProject(),
        customSettings,
        userInitiated = true,
        preferDialog = isConditionalModal,
        showSettingsLink = shouldStartInBackground(),
        indicator = indicator,
      )

      override fun isConditionalModal(): Boolean = customSettings != null

      override fun shouldStartInBackground(): Boolean = !isConditionalModal
    })
  }

  @JvmStatic
  private fun doUpdateAndShowResult(
    project: Project? = null,
    customSettings: UpdateSettings? = null,
    userInitiated: Boolean,
    preferDialog: Boolean,
    showSettingsLink: Boolean,
    indicator: ProgressIndicator? = null,
    callback: ActionCallback? = null,
  ) {
    if (!PropertiesComponent.getInstance().getBoolean(MACHINE_ID_DISABLED_PROPERTY, false) && !machineIdInitialized) {
      machineIdInitialized = true
      val machineId = MachineIdManager.getAnonymizedMachineId("JetBrainsUpdates", "")
      if (machineId != null) {
        UpdateRequestParameters.addParameter(MACHINE_ID_PARAMETER, machineId)
      }
    }

    val updateSettings = customSettings ?: UpdateSettings.getInstance()

    val platformUpdates = getPlatformUpdates(updateSettings, indicator)
    if (platformUpdates is PlatformUpdates.ConnectionError) {
      if (userInitiated) {
        showErrors(project, IdeBundle.message("updates.error.connection.failed", platformUpdates.error.message), preferDialog)
      }
      callback?.setRejected()
      return
    }

    val (pluginUpdates, customRepoPlugins, internalErrors) = getInternalPluginUpdates(
      (platformUpdates as? PlatformUpdates.Loaded)?.newBuild?.apiVersion,
      indicator,
    )

    indicator?.text = IdeBundle.message("updates.external.progress")
    val (externalUpdates, externalErrors) = getExternalPluginUpdates(updateSettings, indicator)

    UpdateSettings.getInstance().saveLastCheckedInfo()

    if (userInitiated && (internalErrors.isNotEmpty() || externalErrors.isNotEmpty())) {
      val builder = HtmlBuilder()
      internalErrors.forEach { (host, ex) ->
        if (!builder.isEmpty) builder.br()

        val message = host?.let {
          IdeBundle.message("updates.plugins.error.message2", it, ex.message)
        } ?: IdeBundle.message("updates.plugins.error.message1", ex.message)
        builder.append(message)
      }
      externalErrors.forEach { (key, value) ->
        if (!builder.isEmpty) builder.br()
        builder.append(IdeBundle.message("updates.external.error.message", key.name, value.message))
      }
      showErrors(project, builder.wrapWithHtmlBody().toString(), preferDialog)
    }

    ApplicationManager.getApplication().invokeLater {
      fun nonIgnored(downloaders: Collection<PluginDownloader>) = downloaders.filterNot { isIgnored(it.descriptor) }

      val enabledPlugins = nonIgnored(pluginUpdates.allEnabled)
      val updatedPlugins = enabledPlugins + nonIgnored(pluginUpdates.allDisabled)

      val forceDialog = preferDialog || userInitiated && !notificationsEnabled()

      if (platformUpdates is PlatformUpdates.Loaded) {
        showResults(
          project,
          platformUpdates,
          updatedPlugins,
          pluginUpdates.incompatible,
          showNotification = userInitiated || WelcomeFrame.getInstance() != null,
          forceDialog,
          showSettingsLink,
        )
      }
      else {
        showResults(
          project,
          updatedPlugins,
          customRepoPlugins,
          externalUpdates,
          enabledPlugins.isNotEmpty(),
          userInitiated,
          forceDialog,
          showSettingsLink,
        )
      }
      callback?.setDone()
    }
  }

  @JvmOverloads
  @JvmStatic
  @JvmName("getPlatformUpdates")
  internal fun getPlatformUpdates(
    settings: UpdateSettings = UpdateSettings.getInstance(),
    indicator: ProgressIndicator? = null,
  ): PlatformUpdates =
    try {
      indicator?.text = IdeBundle.message("updates.checking.platform")
      val productData = loadProductData(indicator)
      if (ExternalUpdateManager.ACTUAL != null || productData == null) {
        PlatformUpdates.Empty
      }
      else {
        UpdateStrategy(ApplicationInfo.getInstance().build, productData, settings).checkForUpdates()
      }
    }
    catch (e: Exception) {
      LOG.infoWithDebug(e)
      when (e) {
        is JDOMException -> PlatformUpdates.Empty  // corrupted content, don't bother telling user
        else -> PlatformUpdates.ConnectionError(e)
      }
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
        HttpRequests.request(url)
          .connect { JDOMUtil.load(it.getReader(indicator)) }
          .let { parseUpdateData(it) }
          ?.also {
            if (it.disableMachineId) {
              PropertiesComponent.getInstance().setValue(MACHINE_ID_DISABLED_PROPERTY, true)
              UpdateRequestParameters.removeParameter(MACHINE_ID_PARAMETER)
            }
        }
      }

      productDataCache = SoftReference(result)
      AppExecutorUtil.getAppScheduledExecutorService().schedule(this::clearProductDataCache, PRODUCT_DATA_TTL_MIN, TimeUnit.MINUTES)
      return@withLock result.getOrThrow()
    }

  private fun clearProductDataCache() {
    if (productDataLock.tryLock(1, TimeUnit.MILLISECONDS)) {  // longer means loading now, no much sense in clearing
      productDataCache = null
      productDataLock.unlock()
    }
  }

  @ApiStatus.Internal
  @JvmStatic
  fun updateDescriptorsForInstalledPlugins(state: InstalledPluginsState) {
    if (ApplicationInfoEx.getInstanceEx().usesJetBrainsPluginRepository()) {
      ApplicationManager.getApplication().executeOnPooledThread {
        val updateable = collectUpdateablePlugins()
        if (updateable.isNotEmpty()) {
          findUpdatesInJetBrainsRepository(updateable, mutableMapOf(), mutableMapOf(), null, state, null)
        }
      }
    }
  }

  /**
   * When [buildNumber] is null, returns new versions of plugins compatible with the current IDE version,
   * otherwise, returns versions compatible with the specified build.
   */
  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  @JvmOverloads
  @JvmStatic
  fun getInternalPluginUpdates(
    buildNumber: BuildNumber? = null,
    indicator: ProgressIndicator? = null,
  ): InternalPluginResults {
    indicator?.text = IdeBundle.message("updates.checking.plugins")
    if (System.getProperty("idea.ignore.disabled.plugins") == null) {
      val brokenPlugins = MarketplaceRequests.getInstance().getBrokenPlugins(ApplicationInfo.getInstance().build)
      if (brokenPlugins.isNotEmpty()) {
        PluginManagerCore.updateBrokenPlugins(brokenPlugins)
      }
    }

    val updateable = collectUpdateablePlugins()
    if (updateable.isEmpty()) {
      return InternalPluginResults(PluginUpdates())
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
        else {
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

    return InternalPluginResults(PluginUpdates(toUpdate.values, toUpdateDisabled.values, incompatible), customRepoPlugins.values, errors)
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

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  private fun findUpdatesInJetBrainsRepository(updateable: MutableMap<PluginId, IdeaPluginDescriptor?>,
                                               toUpdate: MutableMap<PluginId, PluginDownloader>,
                                               toUpdateDisabled: MutableMap<PluginId, PluginDownloader>,
                                               buildNumber: BuildNumber?,
                                               state: InstalledPluginsState,
                                               indicator: ProgressIndicator?) {
    val marketplacePluginIds = MarketplaceRequests.getInstance().getMarketplacePlugins(indicator)
    val idsToUpdate = updateable.keys.filter { it in marketplacePluginIds }.toSet()
    val updates = MarketplaceRequests.getLastCompatiblePluginUpdate(idsToUpdate, buildNumber)
    updateable.forEach { (id, descriptor) ->
      val lastUpdate = updates.find { it.pluginId == id.idString }
      if (lastUpdate != null &&
          (descriptor == null || PluginDownloader.compareVersionsSkipBrokenAndIncompatible(lastUpdate.version, descriptor,
                                                                                           buildNumber) > 0)) {
        runCatching { MarketplaceRequests.loadPluginDescriptor(id.idString, lastUpdate, indicator) }
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

  @JvmOverloads
  @JvmStatic
  fun getExternalPluginUpdates(
    updateSettings: UpdateSettings,
    indicator: ProgressIndicator? = null,
  ): ExternalPluginResults {
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

    return ExternalPluginResults(result, errors)
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
    updatedPlugins: List<PluginDownloader>,
    customRepoPlugins: Collection<PluginNode>,
    externalUpdates: Collection<ExternalUpdate>,
    pluginsEnabled: Boolean,
    userInitiated: Boolean,
    forceDialog: Boolean,
    showSettingsLink: Boolean,
  ) {
    if (pluginsEnabled) {
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
          val updatedPluginNames = updatedPlugins.map { it.pluginName }
          val (title, message) = when (updatedPluginNames.size) {
            1 -> "" to IdeBundle.message("updates.plugin.ready.title", updatedPluginNames[0])
            else -> IdeBundle.message("updates.plugins.ready.title") to updatedPluginNames.joinToString { """"$it"""" }
          }
          showNotification(
            project,
            NotificationKind.PLUGINS,
            "plugins.update.available",
            title,
            message,
            NotificationAction.createExpiring(IdeBundle.message("updates.all.plugins.action", updatedPlugins.size)) { e, _ ->
              PluginUpdateDialog.runUpdateAll(updatedPlugins, e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as JComponent?, null)
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
    else if (!pluginsEnabled) {
      if (forceDialog) {
        NoUpdatesDialog(showSettingsLink).show()
      }
      else if (userInitiated) {
        showNotification(project, NotificationKind.PLUGINS, "no.updates.available", "", NoUpdatesDialog.getNoUpdatesText())
      }
    }
  }

  @RequiresEdt
  private fun showResults(
    project: Project?,
    platformUpdates: PlatformUpdates.Loaded,
    updatedPlugins: List<PluginDownloader>,
    incompatiblePlugins: Collection<IdeaPluginDescriptor>,
    showNotification: Boolean,
    forceDialog: Boolean,
    showSettingsLink: Boolean,
  ) {
    if (showNotification) {
      ourShownNotifications.remove(NotificationKind.PLATFORM)?.forEach { it.expire() }
    }

    val runnable = {
      UpdateInfoDialog(
        project,
        platformUpdates,
        showSettingsLink,
        updatedPlugins,
        incompatiblePlugins,
      ).show()
    }

    if (forceDialog) {
      runnable()
    }
    else {
      UpdateSettingsEntryPointActionProvider.newPlatformUpdate(platformUpdates, updatedPlugins, incompatiblePlugins)

      if (showNotification) {
        IdeUpdateUsageTriggerCollector.NOTIFICATION_SHOWN.log(project)
        val message = IdeBundle.message(
          "updates.new.build.notification.title",
          ApplicationNamesInfo.getInstance().fullProductName,
          platformUpdates.newBuild.version,
        )
        showNotification(
          project,
          NotificationKind.PLATFORM,
          "ide.update.available",
          "",
          message,
          NotificationAction.createSimpleExpiring(IdeBundle.message("updates.notification.update.action")) {
            IdeUpdateUsageTriggerCollector.NOTIFICATION_CLICKED.log(project)
            runnable()
          })
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
    val notification = getNotificationGroup().createNotification(title, XmlStringUtil.wrapInHtml(message), type)
      .setDisplayId(displayId)
      .setCollapseDirection(Notification.CollapseActionsDirection.KEEP_LEFTMOST)
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
  @JvmName("isIgnored")
  internal fun isIgnored(descriptor: IdeaPluginDescriptor): Boolean =
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
  fun testPlatformUpdate(
    project: Project?,
    updateDataText: String,
    patchFile: File?,
    forceUpdate: Boolean,
  ) {
    if (!ApplicationManager.getApplication().isInternal) {
      throw IllegalStateException()
    }

    val currentBuild = ApplicationInfo.getInstance().build
    val productCode = currentBuild.productCode
    val checkForUpdateResult = if (forceUpdate) {
      val node = JDOMUtil.load(updateDataText)
                   .getChild("product")
                   ?.getChild("channel")
                 ?: throw IllegalArgumentException("//channel missing")
      val channel = UpdateChannel(node, productCode)
      val newBuild = channel.builds.firstOrNull()
                     ?: throw IllegalArgumentException("//build missing")
      val patches = newBuild.patches.firstOrNull()
        ?.let { UpdateChain(listOf(it.fromBuild, newBuild.number), it.size) }

      PlatformUpdates.Loaded(newBuild, channel, patches)
    }
    else {
      UpdateStrategy(
        currentBuild,
        parseUpdateData(updateDataText, productCode),
      ).checkForUpdates()
    }

    val dialog = when (checkForUpdateResult) {
      is PlatformUpdates.Loaded -> UpdateInfoDialog(project, checkForUpdateResult, patchFile)
      else -> NoUpdatesDialog(true)
    }

    dialog.show()
  }

  //<editor-fold desc="Deprecated stuff.">
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  @Deprecated(level = DeprecationLevel.ERROR, replaceWith = ReplaceWith("getNotificationGroup()"), message = "Use getNotificationGroup()")
  @Suppress("DEPRECATION")
  @JvmField
  val NOTIFICATIONS =
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
    getInternalPluginUpdates().pluginUpdates.allEnabled.ifEmpty { null }
  //</editor-fold>
}