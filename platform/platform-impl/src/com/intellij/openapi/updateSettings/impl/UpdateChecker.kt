// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.externalComponents.ExternalComponentManager
import com.intellij.ide.externalComponents.ExternalComponentSource
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
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
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.util.Url
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.intellij.util.containers.MultiMap
import com.intellij.util.io.HttpRequests
import com.intellij.util.text.VersionComparatorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil
import kotlinx.coroutines.*
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.lang.ref.SoftReference
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.swing.JComponent
import kotlin.concurrent.withLock

private enum class NotificationKind { PLATFORM, PLUGINS, EXTERNAL }

private val LOG = logger<UpdateChecker>()

private const val DISABLED_UPDATE = "disabled_update.txt"
private const val DISABLED_PLUGIN_UPDATE = "plugin_disabled_updates.txt"
private const val PRODUCT_DATA_TTL_MIN = 5L

private val shownNotifications = MultiMap<NotificationKind, Notification>()

@Service
private class UpdateCheckerHelper(private val coroutineScope: CoroutineScope) {
  @OptIn(ExperimentalCoroutinesApi::class)
  private val limitedDispatcher = Dispatchers.IO.limitedParallelism(1)

  fun updateAndShowResult(showResults: Boolean): ActionCallback {
    val callback = ActionCallback()
    coroutineScope.launch(limitedDispatcher) {
      doUpdateAndShowResult(
        userInitiated = false,
        showResults = showResults,
        preferDialog = false,
        showSettingsLink = true,
        callback = callback,
      )?.let { edtTask ->
        withContext(Dispatchers.EDT) {
          edtTask()
        }
      }
    }
    return callback
  }
}

/**
 * See XML file by [ApplicationInfoEx.getUpdateUrls] for reference.
 */
object UpdateChecker {
  internal const val MACHINE_ID_DISABLED_PROPERTY: String = "machine.id.disabled"

  private val productDataLock = ReentrantLock()
  private var productDataUrl: Url? = null
  private var productDataCache: SoftReference<Product?>? = null
  private val ourUpdatedPlugins: MutableMap<PluginId, PluginDownloader> = HashMap()

  /**
   * Adding a plugin ID to this collection allows excluding a plugin from a regular update check.
   * Has no effect on non-bundled plugins.
   */
  val excludedFromUpdateCheckPlugins: HashSet<String> = hashSetOf()

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
  fun updateAndShowResult(): ActionCallback =
    service<UpdateCheckerHelper>().updateAndShowResult(showResults = true)

  @ApiStatus.Internal
  @IntellijInternalApi
  fun getUpdates(): ActionCallback =
    service<UpdateCheckerHelper>().updateAndShowResult(showResults = false)

  /**
   * For manual update checks (Help | Check for Updates, Settings | Updates | Check Now)
   * (the latter action passes customized update settings and forces result presentation in a dialog).
   */
  @JvmStatic
  @JvmOverloads
  fun updateAndShowResult(project: Project?, customSettings: UpdateSettings? = null) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, IdeBundle.message("updates.checking.progress"), true) {
      override fun run(indicator: ProgressIndicator) {
        doUpdateAndShowResult(
          project = getProject(),
          customSettings = customSettings,
          userInitiated = true,
          showResults = true,
          preferDialog = isConditionalModal,
          showSettingsLink = shouldStartInBackground(),
          indicator = indicator,
        )?.let {
          ApplicationManager.getApplication().invokeLater(it)
        }
      }

      override fun isConditionalModal(): Boolean = customSettings != null

      override fun shouldStartInBackground(): Boolean = !isConditionalModal
    })
  }

  @JvmOverloads
  @JvmStatic
  @JvmName("getPlatformUpdates")
  @ApiStatus.Internal
  @IntellijInternalApi
  internal fun getPlatformUpdates(
    settings: UpdateSettings = UpdateSettings.getInstance(),
    indicator: ProgressIndicator? = null,
  ): PlatformUpdates {
    try {
      indicator?.text = IdeBundle.message("updates.checking.platform")
      val productData = loadProductData(indicator)
      if (productData == null || !settings.isCheckNeeded || ExternalUpdateManager.ACTUAL != null) {
        return PlatformUpdates.Empty
      }
      else {
        return UpdateStrategy(ApplicationInfo.getInstance().build, productData, settings).checkForUpdates()
      }
    }
    catch (e: Exception) {
      val attachTrace = when {
        LOG.isDebugEnabled -> true
        e is IOException -> false
        e is CancellationException -> false
        else -> true
      }
      LOG.info("failed to load update data (${e.javaClass.name}: ${e.message})", if (attachTrace) e else null)
      return PlatformUpdates.ConnectionError(e)
    }
  }

  @JvmStatic
  @Throws(IOException::class, JDOMException::class)
  fun loadProductData(indicator: ProgressIndicator?): Product? {
    val url = ExternalProductResourceUrls.getInstance().updateMetadataUrl ?: return null

    return productDataLock.withLock {
      val cached = productDataCache?.get()
      if (cached != null && url == productDataUrl) cached
      else {
        LOG.debug { "loading ${url}" }
        val product = HttpRequests.request(url)
          .productNameAsUserAgent()
          .connect { JDOMUtil.load(it.getReader(indicator)) }
          .let { parseUpdateData(it) }
          ?.also {
            if (it.disableMachineId) {
              PropertiesComponent.getInstance().setValue(MACHINE_ID_DISABLED_PROPERTY, true)
            }
          }
        productDataCache = SoftReference(product)
        productDataUrl = url
        AppExecutorUtil.getAppScheduledExecutorService().schedule(this::clearProductDataCache, PRODUCT_DATA_TTL_MIN, TimeUnit.MINUTES)
        product
      }
    }
  }

  private fun clearProductDataCache() {
    if (productDataLock.tryLock(1, TimeUnit.MILLISECONDS)) {  // a longer time means loading now, no much sense in clearing
      productDataCache = null
      productDataUrl = null
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

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  @JvmOverloads
  @JvmStatic
  @ApiStatus.Experimental
  fun getPluginUpdates(
    pluginId: PluginId,
    buildNumber: BuildNumber? = null,
    indicator: ProgressIndicator? = null,
  ): PluginUpdatesInfo {
    val updateablePluginsMap = mutableMapOf<PluginId, IdeaPluginDescriptor?>(pluginId to null)
    val result = getInternalPluginUpdates(buildNumber, indicator, updateablePluginsMap)
    val updates = result.pluginUpdates
    return PluginUpdatesInfo(
      updates.allEnabled,
      updates.allDisabled,
      updates.incompatible,
      result.errors
    )
  }

  @ApiStatus.Experimental
  data class PluginUpdatesInfo(
    val allEnabled: Collection<PluginDownloader> = emptyList(),
    val allDisabled: Collection<PluginDownloader> = emptyList(),
    val incompatible: Collection<IdeaPluginDescriptor> = emptyList(),
    val errors: Map<String?, Exception> = emptyMap()
  ) {
    val all: List<PluginDownloader> by lazy {
      allEnabled + allDisabled
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
  @IntellijInternalApi
  @ApiStatus.Internal
  @Deprecated("Use [getPluginUpdates] instead", ReplaceWith("getPluginUpdates(pluginId, buildNumber, indicator)"))
  fun getInternalPluginUpdates(
    buildNumber: BuildNumber? = null,
    indicator: ProgressIndicator? = null,
    updateablePluginsMap: MutableMap<PluginId, IdeaPluginDescriptor?>? = null,
  ): InternalPluginResults {
    indicator?.text = IdeBundle.message("updates.checking.plugins")
    if (!PluginEnabler.HEADLESS.isIgnoredDisabledPlugins) {
      val brokenPlugins = MarketplaceRequests.getBrokenPlugins(ApplicationInfo.getInstance().build)
      if (!brokenPlugins.isNullOrEmpty()) {
        updateBrokenPlugins(brokenPlugins)
      }
    }

    val updateable: MutableMap<PluginId, IdeaPluginDescriptor?>
    if (updateablePluginsMap == null) {
      updateable = collectUpdateablePlugins()
    }
    else {
      updateable = updateablePluginsMap
    }
    if (updateable.isEmpty()) {
      return InternalPluginResults(PluginUpdates())
    }

    val toUpdate = HashMap<PluginId, PluginDownloader>()
    val toUpdateDisabled = HashMap<PluginId, PluginDownloader>()
    val customRepoPlugins = HashMap<PluginId, PluginUiModel>()
    val errors = LinkedHashMap<String?, Exception>()
    val state = InstalledPluginsState.getInstance()
    for (host in RepositoryHelper.getPluginHosts()) {
      try {
        if (host == null && ApplicationInfoEx.getInstanceEx().usesJetBrainsPluginRepository()) {
          findUpdatesInJetBrainsRepository(updateable, toUpdate, toUpdateDisabled, buildNumber, state, indicator)
        }
        else {
          RepositoryHelper.loadPluginModels(host, buildNumber, indicator).forEach { model ->
            val id = model.pluginId
            if (updateable.contains(id)) {
              updateable.remove(id)
              prepareDownloader(state, model, buildNumber, toUpdate, toUpdateDisabled, indicator, host)
            }
            // collect latest plugins from custom repos
            val storedDescriptor = customRepoPlugins[id]
            if (storedDescriptor == null ||
                (VersionComparatorUtil.compare(model.version, storedDescriptor.version) > 0 && allowedUpgrade(storedDescriptor.getDescriptor(), model.getDescriptor())) ||
                (VersionComparatorUtil.compare(model.version, storedDescriptor.version) < 0 && allowedDowngrade(storedDescriptor.getDescriptor(), model.getDescriptor()))) {
              customRepoPlugins[id] = model
            }
          }
        }
      }
      catch (e: Exception) {
        LOG.info("failed to load plugins from ${host ?: "default repository"}: ${e.message}", if (LOG.isDebugEnabled) e else null)
        errors[host] = e
      }
    }

    val incompatible = if (buildNumber == null) emptyList()
    else {
      // collecting plugins that aren't going to be updated and are incompatible with the new build
      // (the map may contain updateable and already-updated bundled plugins -
      // those are expected to have a compatible version in the IDE)
      updateable.values.asSequence()
        .filterNotNull()
        .filter { it.isEnabled }
        .filterNot { it.isBundled || it.allowBundledUpdate() }
        .filterNot { PluginManagerCore.isCompatible(it, buildNumber) }
        .toSet()
    }

    return InternalPluginResults(PluginUpdates(toUpdate.values, toUpdateDisabled.values, incompatible), customRepoPlugins.values, errors)
  }

  private fun collectUpdateablePlugins(): MutableMap<PluginId, IdeaPluginDescriptor?> {
    val updateable = HashMap<PluginId, IdeaPluginDescriptor?>()

    // installed plugins that could be updated (either downloaded or updateable bundled)
    PluginManagerCore.plugins
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

    // excluding plugins that take care of their own updates
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

  @JvmStatic
  private fun allowedDowngrade(localDescriptor: IdeaPluginDescriptor?, remoteDescriptor: IdeaPluginDescriptor?): Boolean {
    return PluginManagementPolicy.getInstance().isDowngradeAllowed(localDescriptor, remoteDescriptor)
  }

  @JvmStatic
  private fun allowedUpgrade(localDescriptor: IdeaPluginDescriptor?, remoteDescriptor: IdeaPluginDescriptor?): Boolean {
    return PluginManagementPolicy.getInstance().isUpgradeAllowed(localDescriptor, remoteDescriptor)
  }

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  private fun findUpdatesInJetBrainsRepository(
    updateable: MutableMap<PluginId, IdeaPluginDescriptor?>,
    toUpdate: MutableMap<PluginId, PluginDownloader>,
    toUpdateDisabled: MutableMap<PluginId, PluginDownloader>,
    buildNumber: BuildNumber?,
    state: InstalledPluginsState,
    indicator: ProgressIndicator?,
  ) {
    val marketplacePluginIds = MarketplaceRequests.getInstance().getMarketplacePlugins(indicator)
    val idsToUpdate = updateable.keys.filter { it in marketplacePluginIds }.toSet()
    val updates = MarketplaceRequests.getLastCompatiblePluginUpdate(idsToUpdate, buildNumber)
    for ((id, descriptor) in updateable) {
      val lastUpdate = updates.find { it.pluginId == id.idString }
      if (lastUpdate != null &&
          (descriptor == null || PluginDownloader.compareVersionsSkipBrokenAndIncompatible(lastUpdate.version, descriptor, buildNumber) > 0)) {
        runCatching { MarketplaceRequests.loadPluginModel(id.idString, lastUpdate, indicator) }
          .onFailure {
            if (!isNetworkError(it)) throw it

            LOG.warn("Unable to read update metadata for plugin: $id, ${it::class.java} ${it.message}")
          }
          .onSuccess { it.externalPluginIdForScreenShots = lastUpdate.externalPluginId }
          .onSuccess { prepareDownloader(state, it, buildNumber, toUpdate, toUpdateDisabled, indicator, null) }
      }
    }
    (toUpdate.keys.asSequence() + toUpdateDisabled.keys.asSequence()).forEach { updateable.remove(it) }
  }

  private fun isNetworkError(it: Throwable): Boolean {
    return it is SocketTimeoutException
           || it is UnknownHostException
           || it is HttpRequests.HttpStatusException && it.statusCode == HttpURLConnection.HTTP_NOT_FOUND
  }

  @RequiresBackgroundThread
  private fun prepareDownloader(
    state: InstalledPluginsState,
    descriptor: PluginUiModel,
    buildNumber: BuildNumber?,
    toUpdate: MutableMap<PluginId, PluginDownloader>,
    toUpdateDisabled: MutableMap<PluginId, PluginDownloader>,
    indicator: ProgressIndicator?,
    host: String?,
  ) {
    val downloader = PluginDownloader.createDownloader(descriptor, host, buildNumber)
    state.onDescriptorDownload(descriptor)
    checkAndPrepareToInstall(downloader, state, if (PluginManagerCore.isDisabled(downloader.id)) toUpdateDisabled else toUpdate,
                             buildNumber, indicator)
  }

  @JvmOverloads
  @JvmStatic
  @IntellijInternalApi
  @ApiStatus.Internal
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
  @RequiresBackgroundThread
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
        || (PluginDownloader.compareVersionsSkipBrokenAndIncompatible(pluginVersion, installedPlugin, buildNumber) > 0
            && allowedUpgrade(installedPlugin, originalDownloader.descriptor))
        || (PluginDownloader.compareVersionsSkipBrokenAndIncompatible(pluginVersion, installedPlugin, buildNumber) < 0
            && allowedDowngrade(installedPlugin, originalDownloader.descriptor))) {
      val oldDownloader = ourUpdatedPlugins[pluginId]
      val downloader = if (PluginManagerCore.isDisabled(pluginId)) {
        originalDownloader
      }
      else if (oldDownloader == null
               || (VersionComparatorUtil.compare(pluginVersion, oldDownloader.pluginVersion) > 0
                   && allowedUpgrade(installedPlugin, oldDownloader.descriptor))
               || (VersionComparatorUtil.compare(pluginVersion, oldDownloader.pluginVersion) < 0 &&
                   allowedDowngrade(installedPlugin, oldDownloader.descriptor))) {
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

  @JvmStatic
  val disabledToUpdate: Set<PluginId> by lazy { TreeSet(readConfigLines(DISABLED_UPDATE).map { PluginId.getId(it) }) }

  @JvmStatic
  fun saveDisabledToUpdatePlugins() {
    PluginStringSetFile.writeIdsSafe(PathManager.getConfigDir().resolve(DISABLED_UPDATE), disabledToUpdate, LOG)
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

  /** A helper method for manually testing platform updates (see [com.intellij.internal.ShowUpdateInfoDialogAction]). */
  @ApiStatus.Internal
  @IntellijInternalApi
  fun testPlatformUpdate(
    project: Project?,
    updateDataText: String,
    patchFile: Path?,
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
      is PlatformUpdates.Loaded -> PlatformUpdateDialog.createTestDialog(project, checkForUpdateResult, patchFile)
      else -> NoUpdatesDialog(true)
    }

    dialog.show()
  }

  //<editor-fold desc="Deprecated stuff.">
  @ApiStatus.ScheduledForRemoval
  @Deprecated(level = DeprecationLevel.ERROR, replaceWith = ReplaceWith("getNotificationGroup()"), message = "Use getNotificationGroup()")
  @Suppress("DEPRECATION", "unused")
  @JvmField
  val NOTIFICATIONS: NotificationGroup = NotificationGroup("IDE and Plugin Updates", NotificationDisplayType.STICKY_BALLOON, true, null,
                                                           null, null, PluginManagerCore.CORE_ID)

  @get:ApiStatus.ScheduledForRemoval
  @get:Deprecated(message = "Use disabledToUpdate", replaceWith = ReplaceWith("disabledToUpdate"))
  @Deprecated(message = "Use disabledToUpdate", replaceWith = ReplaceWith("disabledToUpdate"))
  @Suppress("unused")
  @JvmStatic
  val disabledToUpdatePlugins: Set<String>
    get() = disabledToUpdate.mapTo(TreeSet()) { it.idString }

  @ApiStatus.ScheduledForRemoval
  @Deprecated(message = "Use checkForPluginUpdates", replaceWith = ReplaceWith(""))
  @JvmStatic
  fun getPluginUpdates(): Collection<PluginDownloader>? =
    getInternalPluginUpdates().pluginUpdates.allEnabled.ifEmpty { null }
  //</editor-fold>
}

private fun doUpdateAndShowResult(
  project: Project? = null,
  customSettings: UpdateSettings? = null,
  userInitiated: Boolean,
  showResults: Boolean,
  preferDialog: Boolean,
  showSettingsLink: Boolean,
  indicator: ProgressIndicator? = null,
  callback: ActionCallback? = null,
): (() -> Unit)? {
  val updateSettings = customSettings ?: UpdateSettings.getInstance()

  val platformUpdates = UpdateChecker.getPlatformUpdates(updateSettings, indicator)
  if (platformUpdates is PlatformUpdates.ConnectionError) {
    if (userInitiated) {
      val err = platformUpdates.error
      val message = when {
        err is HttpRequests.HttpStatusException && err.statusCode == HttpRequests.CUSTOM_ERROR_CODE && err.message != null -> err.message!!
        else -> IdeBundle.message("updates.error.connection.failed", err.message)
      }
      showErrors(project, message, preferDialog)
    }
    callback?.setRejected()
    return null
  }

  val (pluginUpdates, customRepoPlugins, internalErrors) = UpdateChecker.getInternalPluginUpdates(
    (platformUpdates as? PlatformUpdates.Loaded)?.newBuild?.apiVersion,
    indicator,
  )

  indicator?.text = IdeBundle.message("updates.external.progress")
  val (externalUpdates, externalErrors) = UpdateChecker.getExternalPluginUpdates(updateSettings, indicator)

  UpdateSettings.getInstance().saveLastCheckedInfo()

  if (userInitiated && (internalErrors.isNotEmpty() || externalErrors.isNotEmpty())) {
    val builder = HtmlBuilder()
    internalErrors.forEach { (host, ex) ->
      if (!builder.isEmpty) {
        builder.br()
      }

      val message = host?.let {
        IdeBundle.message("updates.plugins.error.message2", it, ex.message)
      } ?: IdeBundle.message("updates.plugins.error.message1", ex.message)
      builder.append(message)
    }
    for ((key, value) in externalErrors) {
      if (!builder.isEmpty) builder.br()
      builder.append(IdeBundle.message("updates.external.error.message", key.name, value.message))
    }
    showErrors(project = project, message = builder.wrapWithHtmlBody().toString(), preferDialog = preferDialog)
  }

  fun nonIgnored(downloaders: Collection<PluginDownloader>) = downloaders.filterNot { UpdateChecker.isIgnored(it.descriptor) }

  val updatesForEnabledPlugins = nonIgnored(pluginUpdates.allEnabled)
  // disabled plugins are excluded from updates, see IDEA-273418, TODO refactor
  // probably it can lead to disabled plugins becoming incompatible without a notification in platform update dialog
  val updatesForPlugins = updatesForEnabledPlugins // + nonIgnored(pluginUpdates.allDisabled)

  // TODO revise this
  val pluginAutoUpdateService = service<PluginAutoUpdateService>()
  if (platformUpdates !is PlatformUpdates.Loaded) {
    pluginAutoUpdateService.onPluginUpdatesChecked(updatesForPlugins)
  }
  else {
    if (pluginAutoUpdateService.isAutoUpdateEnabled()) {
      val (pluginUpdates, _) = UpdateChecker.getInternalPluginUpdates(indicator = indicator)
      pluginAutoUpdateService.onPluginUpdatesChecked(nonIgnored(pluginUpdates.allEnabled))
    }
  }

  if (!showResults) {
    if (platformUpdates is PlatformUpdates.Loaded) {
      UpdateSettingsEntryPointActionProvider.newPlatformUpdate(platformUpdates, updatesForPlugins, pluginUpdates.incompatible)
    }
    else {
      UpdateSettingsEntryPointActionProvider.newPluginUpdates(updatesForPlugins, customRepoPlugins)
    }
    callback?.setDone()
    return null
  }
  return {
    val forceDialog = preferDialog || userInitiated && !notificationsEnabled()

    if (platformUpdates is PlatformUpdates.Loaded) {
      showResults(
        project = project,
        platformUpdates = platformUpdates,
        updatesForPlugins = updatesForPlugins,
        incompatiblePlugins = pluginUpdates.incompatible,
        showNotification = userInitiated || WelcomeFrame.getInstance() != null,
        forceDialog = forceDialog,
        showSettingsLink = showSettingsLink,
      )
    }
    else {
      showResults(
        project = project,
        updatesForPlugins = updatesForPlugins,
        customRepoPlugins = customRepoPlugins,
        externalUpdates = externalUpdates,
        updatesForEnabledPlugins = updatesForEnabledPlugins,
        userInitiated = userInitiated,
        forceDialog = forceDialog,
        showSettingsLink = showSettingsLink,
      )
    }
    callback?.setDone()
  }
}

private fun showErrors(project: Project?, @NlsContexts.DialogMessage message: String, preferDialog: Boolean) {
  if (preferDialog) {
    UIUtil.invokeLaterIfNeeded { Messages.showErrorDialog(project, message, IdeBundle.message("updates.error.connection.title")) }
  }
  else {
    UpdateChecker.getNotificationGroup().createNotification(message, NotificationType.WARNING).notify(project)
  }
}

@RequiresEdt
private fun showResults(
  project: Project?,
  updatesForPlugins: List<PluginDownloader>,
  customRepoPlugins: Collection<PluginUiModel>,
  externalUpdates: Collection<ExternalUpdate>,
  updatesForEnabledPlugins: List<PluginDownloader>,
  userInitiated: Boolean,
  forceDialog: Boolean,
  showSettingsLink: Boolean,
) {
  if (updatesForEnabledPlugins.isNotEmpty()) {
    if (userInitiated) {
      shownNotifications.remove(NotificationKind.PLUGINS)?.forEach { it.expire() }
    }

    // offer all updates in a dialog
    val showUpdateDialog = { PluginUpdateDialog(project, updatesForPlugins, customRepoPlugins).show() }

    if (forceDialog) {
      showUpdateDialog()
    }
    else {
      UpdateSettingsEntryPointActionProvider.newPluginUpdates(updatesForPlugins, customRepoPlugins)

      if (userInitiated) {
        // offer to update only enabled plugins
        showUpdatePluginsNotification(updatesForEnabledPlugins, project, showUpdateDialog)
      }
    }
  }

  if (externalUpdates.isNotEmpty()) {
    shownNotifications.remove(NotificationKind.EXTERNAL)?.forEach { it.expire() }

    for (update in externalUpdates) {
      val runnable = { update.source.installUpdates(update.components) }
      if (forceDialog) {
        runnable()
      }
      else {
        val message = IdeBundle.message("updates.external.ready.message", update.components.size,
                                        update.components.joinToString(", "))
        showNotification(
          project = project,
          kind = NotificationKind.EXTERNAL,
          displayId = "external.components.available",
          title = "",
          message = message,
          actions = listOf(NotificationAction.createSimpleExpiring(IdeBundle.message("updates.notification.update.action"), runnable)))
      }
    }
  }
  else if (updatesForEnabledPlugins.isEmpty()) {
    if (forceDialog) {
      NoUpdatesDialog(showSettingsLink).show()
    }
    else if (userInitiated) {
      showNotification(project = project,
                       kind = NotificationKind.PLUGINS,
                       displayId = "no.updates.available",
                       title = "",
                       message = NoUpdatesDialog.getNoUpdatesText())
    }
  }
}

private fun showUpdatePluginsNotification(
  updatesForPlugins: List<PluginDownloader>,
  project: Project?,
  showUpdateDialog: () -> Unit,
) {
  val updatedPluginNames = updatesForPlugins.map { it.pluginName }
  val (title, message) = when (updatedPluginNames.size) {
    1 -> "" to IdeBundle.message("updates.plugin.ready.title", updatedPluginNames[0])
    else -> IdeBundle.message("updates.plugins.ready.title") to updatedPluginNames.joinToString { """"$it"""" }
  }
  showNotification(
    project = project,
    kind = NotificationKind.PLUGINS,
    displayId = "plugins.update.available",
    title = title,
    message = message,
    actions = listOf(
      NotificationAction.createExpiring(IdeBundle.message("updates.all.plugins.action", updatesForPlugins.size)) { e, _ ->
        val component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as JComponent?
        PluginUpdateDialog.runUpdateAll(updatesForPlugins, component, null, null)
      },
      NotificationAction.createSimpleExpiring(IdeBundle.message("updates.plugins.dialog.action"), showUpdateDialog),
      NotificationAction.createSimpleExpiring(IdeBundle.message("updates.ignore.updates.link", updatesForPlugins.size)) {
        UpdateChecker.ignorePlugins(updatesForPlugins.map { it.descriptor })
      },
    ),
  )
}

@RequiresEdt
private fun showResults(
  project: Project?,
  platformUpdates: PlatformUpdates.Loaded,
  updatesForPlugins: List<PluginDownloader>,
  incompatiblePlugins: Collection<IdeaPluginDescriptor>,
  showNotification: Boolean,
  forceDialog: Boolean,
  showSettingsLink: Boolean,
) {
  if (showNotification) {
    shownNotifications.remove(NotificationKind.PLATFORM)?.forEach { it.expire() }
  }

  val showUpdateDialog = {
    PlatformUpdateDialog(
      project,
      platformUpdates,
      showSettingsLink,
      updatesForPlugins,
      incompatiblePlugins,
    ).show()
  }

  if (forceDialog) {
    showUpdateDialog()
  }
  else {
    UpdateSettingsEntryPointActionProvider.newPlatformUpdate(platformUpdates, updatesForPlugins, incompatiblePlugins)

    if (showNotification) {
      IdeUpdateUsageTriggerCollector.NOTIFICATION_SHOWN.log(project)
      val message = IdeBundle.message(
        "updates.new.build.notification.title",
        ApplicationNamesInfo.getInstance().fullProductName,
        platformUpdates.newBuild.version,
      )
      showNotification(
        project = project,
        kind = NotificationKind.PLATFORM,
        displayId = "ide.update.available",
        title = "",
        message = message,
        actions = listOf(NotificationAction.createSimpleExpiring(IdeBundle.message("updates.notification.update.action")) {
          IdeUpdateUsageTriggerCollector.NOTIFICATION_CLICKED.log(project)
          showUpdateDialog()
        }))
    }
  }
}

private fun showNotification(
  project: Project?,
  kind: NotificationKind,
  displayId: String,
  @NlsContexts.NotificationTitle title: String,
  @NlsContexts.NotificationContent message: String,
  actions: List<NotificationAction> = emptyList(),
) {
  val type = if (kind == NotificationKind.PLATFORM) NotificationType.IDE_UPDATE else NotificationType.INFORMATION
  val notification = UpdateChecker.getNotificationGroup().createNotification(title, XmlStringUtil.wrapInHtml(message), type)
    .setDisplayId(displayId)
    .setCollapseDirection(Notification.CollapseActionsDirection.KEEP_LEFTMOST)
  notification.whenExpired { shownNotifications.remove(kind, notification) }
  for (action in actions) {
    notification.addAction(action)
  }
  notification.notify(project)
  shownNotifications.putValue(kind, notification)
}

private fun notificationsEnabled(): Boolean {
  return NotificationsConfiguration.getNotificationsConfiguration().let {
    it.areNotificationsEnabled() && it.getDisplayType(UpdateChecker.getNotificationGroup().displayId) != NotificationDisplayType.NONE
  }
}
