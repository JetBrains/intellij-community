// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.DynamicPlugins
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.ide.plugins.PluginDependencyImpl
import com.intellij.ide.plugins.PluginInstaller
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.ide.plugins.PluginManagementPolicy
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.getMainDescriptor
import com.intellij.ide.plugins.isBrokenPlugin
import com.intellij.ide.plugins.loadDescriptorFromArtifact
import com.intellij.ide.plugins.marketplace.MarketplacePluginDownloadService
import com.intellij.ide.plugins.marketplace.PluginSignatureChecker
import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls
import com.intellij.ide.plugins.newui.PluginDependencyModel
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUiModelAdapter
import com.intellij.ide.plugins.readBasicDescriptorDataFromArtifact
import com.intellij.internal.statistic.DeviceIdManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.text.VersionComparatorUtil
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path
import java.time.Instant
import java.util.Date
import java.util.Objects
import java.util.function.Consumer
import javax.swing.JComponent

class PluginDownloader private constructor(
  model: PluginUiModel,
  pluginUrl: String,
  buildNumber: BuildNumber?,
  errorsConsumer: Consumer<@NotificationContent String>,
  service: MarketplacePluginDownloadService?,
) {
  private val myPluginId: PluginId = model.pluginId
  private val myPluginName: String? = model.name
  private val myProductCode: String? = model.productCode
  private val myReleaseDate: Date? = model.releaseDate?.let { Date.from(Instant.ofEpochMilli(it)) }
  private val myReleaseVersion: Int = model.releaseVersion
  private val myLicenseOptional: Boolean = model.isLicenseOptional
  private val myDescription: String? = model.description
  private val myDependencies: List<PluginDependencyModel> = model.dependencies

  private val myPluginUrl: String = pluginUrl
  private val myBuildNumber: BuildNumber? = buildNumber

  private val myErrorsConsumer: Consumer<@NotificationContent String> = errorsConsumer
  private val myDownloadService: MarketplacePluginDownloadService? = service

  private var myPluginVersion: String? = model.version
  private val myModel: PluginUiModel = model
  private var myDescriptor: IdeaPluginDescriptor = model.getDescriptor()
  private var myFile: Path? = null
  private var myOldFile: Path? = null
  private var myShownErrors: Boolean = false

  fun withErrorsConsumer(errorsConsumer: Consumer<@NotificationContent String>): PluginDownloader {
    return PluginDownloader(myModel, myPluginUrl, myBuildNumber, errorsConsumer, myDownloadService)
  }

  fun withDownloadService(downloadService: MarketplacePluginDownloadService?): PluginDownloader {
    return PluginDownloader(myModel, myPluginUrl, myBuildNumber, myErrorsConsumer, downloadService)
  }

  @get:ApiStatus.ScheduledForRemoval
  @get:Deprecated("Use id", ReplaceWith("id"))
  val pluginId: String
    get() = myPluginId.idString

  val id: PluginId
    get() = myPluginId

  val pluginVersion: @NlsSafe String?
    get() = myPluginVersion

  val pluginName: @NlsSafe String
    get() = myPluginName ?: myPluginId.idString

  val productCode: String?
    get() = myProductCode

  val releaseDate: Date?
    get() = myReleaseDate

  val releaseVersion: Int
    get() = myReleaseVersion

  fun isFromMarketplace(): Boolean {
    try {
      return Objects.equals(URI(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST).host, URI(myPluginUrl).host)
    }
    catch (_: URISyntaxException) {
      return false
    }
  }

  val isLicenseOptional: Boolean
    get() = myLicenseOptional

  val buildNumber: BuildNumber?
    get() = myBuildNumber

  val descriptor: IdeaPluginDescriptor
    get() = myDescriptor

  @get:ApiStatus.Internal
  val uiModel: PluginUiModel
    get() = myModel

  @Throws(IOException::class)
  fun getFilePath(): Path {
    val file = myFile
    if (file == null) {
      throw IOException("Plugin '$pluginName' was not successfully downloaded")
    }
    return file
  }

  val isShownErrors: Boolean
    get() = myShownErrors

  @Throws(IOException::class)
  fun prepareToInstall(indicator: ProgressIndicator?): Boolean {
    ThreadingAssertions.assertBackgroundThread()
    myShownErrors = false

    if (myFile != null) {
      val actualDescriptor = loadDescriptorFromArtifact()
      if (actualDescriptor == null) {
        reportError(IdeBundle.message("error.descriptor.load.failed", myFile))
        return false
      }
      myDescriptor = actualDescriptor
      return true
    }

    var descriptor: IdeaPluginDescriptor? = null

    if (PluginManagerCore.isPluginInstalled(myPluginId)) {
      descriptor = PluginManagerCore.getPlugin(myPluginId)
      LOG.assertTrue(descriptor != null)
      val installedDescriptor = descriptor!!

      val pluginVersion = myPluginVersion
      if (pluginVersion != null) {
        val result = compareVersionsSkipBrokenAndIncompatible(pluginVersion, installedDescriptor)
        if (result < 0 && isDowngradeAllowed(installedDescriptor)) {
          LOG.info("Preparing to downgrade plugin '" + myPluginId + "' : " + pluginVersion + " -> " + installedDescriptor.version)
        }
        else if (result <= 0) {
          LOG.info("Preparing: plugin $myPluginId: current version (max) $pluginVersion")
          return false
        }
      }

      myOldFile = if (installedDescriptor.isBundled) null else installedDescriptor.pluginPath
    }

    val file: Path
    try {
      file = tryDownloadPlugin(indicator)
      myFile = file
    }
    catch (e: IOException) {
      LOG.info(e)
      val message = e.message
      reportError(message ?: IdeBundle.message("unknown.error"))
      return false
    }

    val loaded = LoadingState.COMPONENTS_LOADED.isOccurred  // plugins can be requested during initial IDE setup
    if (loaded && !PluginSignatureChecker.verifyIfRequired(myDescriptor, file, isFromMarketplace(), true)) {
      myShownErrors = true
      return false
    }

    val actualDescriptor = loadDescriptorFromArtifact()
    if (actualDescriptor == null) {
      reportError(IdeBundle.message("error.downloaded.descriptor.load.failed"))
      return false
    }

    if (loaded && InstalledPluginsState.getInstance().wasUpdated(actualDescriptor.pluginId)) {
      reportError(IdeBundle.message("error.pending.update", pluginName))
      return false
    }

    val pluginVersion: String = actualDescriptor.version
    myPluginVersion = pluginVersion

    if (descriptor != null) {
      val result = compareVersionsSkipBrokenAndIncompatible(pluginVersion, descriptor)
      if (result < 0 && isDowngradeAllowed(descriptor)) {
        LOG.info("Downgrading plugin '" + myPluginId + "' : " + pluginVersion + " -> " + descriptor.version)
      }
      else if (result <= 0) {
        LOG.info("Plugin $myPluginId: current version (max) $pluginVersion")
        reportError(IdeBundle.message("error.older.update", pluginVersion, descriptor.version))
        return false
      }
    }

    myDescriptor = actualDescriptor

    val buildNumber = myBuildNumber ?: PluginManagerCore.buildNumber
    val incompatibleError = PluginManagerCore.checkBuildNumberCompatibility(actualDescriptor, buildNumber)
    if (incompatibleError != null) {
      LOG.info(
        "Plugin " + myPluginId + " is incompatible with current installation " +
        "(since:" + actualDescriptor.sinceBuild + " until:" + actualDescriptor.untilBuild + ")",
      )
      reportError(IdeBundle.message("error.incompatible.update", XmlStringUtil.escapeString(incompatibleError.detailedMessage)))
      return false
    }

    return true
  }

  private fun isDowngradeAllowed(localDescriptor: IdeaPluginDescriptor): Boolean {
    return PluginManagementPolicy.getInstance().isDowngradeAllowed(localDescriptor, myDescriptor)
  }

  @Throws(IOException::class)
  private fun loadDescriptorFromArtifact(): IdeaPluginDescriptorImpl? {
    ThreadingAssertions.assertBackgroundThread()
    return if (myBuildNumber == null) {
      loadDescriptorFromArtifact(getFilePath(), null)
    }
    else {
      readBasicDescriptorDataFromArtifact(getFilePath())
    }
  }

  private fun reportError(@NotificationContent errorMessage: String) {
    LOG.info("PluginDownloader error: $errorMessage")
    myShownErrors = true
    myErrorsConsumer.accept(IdeBundle.message("error.plugin.was.not.installed", pluginName, errorMessage))
  }

  @Throws(IOException::class)
  fun install() {
    PluginInstaller.installAfterRestartAndKeepIfNecessary(myDescriptor, getFilePath(), myOldFile)

    if (LoadingState.COMPONENTS_LOADED.isOccurred) {
      val isInstalled = PluginManagerCore.isPluginInstalled(myDescriptor.pluginId)
      InstalledPluginsState.getInstance().onPluginInstall(myDescriptor, isInstalled, true)
    }
    else {
      InstalledPluginsState.addPreInstalledPlugin(myDescriptor)
    }
  }

  @RequiresEdt(generateAssertion = false)
  @Throws(IOException::class)
  fun installDynamically(ownerComponent: JComponent?): Boolean {
    assert(myDescriptor is PluginMainDescriptor)
    val descriptor = myDescriptor as PluginMainDescriptor

    val canLoad = runWithModalProgressBlocking(ModalTaskOwner.guess(), "") { // FIXME this is an ad-hoc to run BGT method on EDT
      DynamicPlugins.validateCanLoadWithoutRestart(descriptor) == null
    }

    val appliedWithoutRestart =
      canLoad &&
      (myOldFile == null || unloadDescriptorById(descriptor.pluginId)) &&
      PluginInstaller.installAndLoadDynamicPlugin(getFilePath(), ownerComponent, descriptor)

    if (!appliedWithoutRestart) {
      install()
    }

    return appliedWithoutRestart
  }

  @VisibleForTesting
  @ApiStatus.Internal
  @Throws(IOException::class)
  fun tryDownloadPlugin(indicator: ProgressIndicator?): Path {
    ThreadingAssertions.assertBackgroundThread()
    if (indicator != null) {
      indicator.checkCanceled()
      indicator.setText2(IdeBundle.message("progress.downloading.plugin", pluginName))
    }

    LOG.info("downloading plugin $myPluginName($myPluginId) version $myPluginVersion from $myPluginUrl")
    val downloader = myDownloadService ?: MarketplacePluginDownloadService()
    val oldFile = myOldFile
    return if (oldFile != null) {
      downloader.downloadPluginViaBlockMap(myPluginUrl, oldFile, indicator)
    }
    else {
      downloader.downloadPlugin(myPluginUrl, indicator)
    }
  }

  @ApiStatus.Internal
  fun checkPluginCanBeDownloaded(indicator: ProgressIndicator?): Boolean {
    val downloader = myDownloadService ?: MarketplacePluginDownloadService()
    return downloader.checkPluginCanBeDownloaded(myPluginUrl, indicator)
  }

  fun toPluginNode(): PluginNode {
    val descriptor = descriptor
    if (descriptor is PluginNode) {
      return descriptor
    }

    val node = PluginNode(id)
    node.name = pluginName
    node.productCode = productCode
    node.releaseDate = releaseDate
    node.releaseVersion = releaseVersion
    node.isLicenseOptional = isLicenseOptional
    node.setVersion(pluginVersion)
    node.downloadUrl = myPluginUrl
    node.setDependencies(myDependencies.map { dep -> PluginDependencyImpl(dep.pluginId, null, dep.isOptional) })
    node.description = myDescription
    return node
  }

  // This is a hacky way to get around the fact that prepareToInstall does more than it should. FIXME
  internal fun setOldFile(oldFile: Path?) {
    myOldFile = oldFile
  }

  companion object {
    private val LOG: Logger = Logger.getInstance(PluginDownloader::class.java)

    @JvmStatic
    fun getMarketplaceDownloadsUUID(): String {
      try {
        return DeviceIdManager.getOrGenerateId(object : DeviceIdManager.DeviceIdToken {}, "MarketplaceDownloads")
      }
      catch (_: DeviceIdManager.InvalidDeviceIdTokenException) {
        return ""
      }
    }

    @JvmStatic
    fun showErrorDialog(@NotificationContent text: String) {
      val app = ApplicationManager.getApplication()
      if (app != null && !app.isDisposed) {
        app.invokeLater({ Messages.showErrorDialog(text, IdeBundle.message("title.plugin.installation")) }, ModalityState.any())
      }
    }

    @JvmStatic
    fun compareVersionsSkipBrokenAndIncompatible(newPluginVersion: String, existingPlugin: IdeaPluginDescriptor): Int {
      return compareVersionsSkipBrokenAndIncompatible(newPluginVersion, existingPlugin, null)
    }

    @JvmStatic
    fun compareVersionsSkipBrokenAndIncompatible(
      newPluginVersion: String,
      existingPlugin: IdeaPluginDescriptor,
      newBuildNumber: BuildNumber?,
    ): Int {
      var state = VersionComparatorUtil.compare(newPluginVersion, existingPlugin.version)
      if (state < 0 && (isBrokenPlugin(existingPlugin) || PluginManagerCore.isIncompatible(existingPlugin, newBuildNumber))) {
        state = 1
      }
      return state
    }

    @JvmStatic
    @Throws(IOException::class)
    fun createDownloader(descriptor: IdeaPluginDescriptor): PluginDownloader {
      return createDownloader(descriptor, null, null)
    }

    @RequiresEdt(generateAssertion = false)
    private fun unloadDescriptorById(pluginId: PluginId): Boolean {
      val plugin = PluginManagerCore.findPlugin(pluginId)?.getMainDescriptor() ?: return false
      val canUnload = runWithModalProgressBlocking(ModalTaskOwner.guess(), "") { // FIXME this is an ad-hoc to run BGT method on EDT
        DynamicPlugins.validateCanUnloadWithoutRestart(plugin) == null
      }
      if (!canUnload) {
        return false
      }
      val options = DynamicPlugins.UnloadPluginOptions().withDisable(false).withUpdate(true).withWaitForClassloaderUnload(true)
      return DynamicPlugins.unloadPlugin(plugin, options)
    }

    @JvmStatic
    @ApiStatus.Internal
    @Throws(IOException::class)
    fun createDownloader(
      pluginUiModel: PluginUiModel,
      host: String?, // FIXME this should come from pluginUiModel (there is repositoryName)
      buildNumber: BuildNumber?,
    ): PluginDownloader {
      return createDownloader(pluginUiModel, host, buildNumber, pluginUiModel.downloadUrl, pluginUiModel.isFromMarketplace)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun createDownloader(
      descriptor: IdeaPluginDescriptor,
      host: String?,
      buildNumber: BuildNumber?,
    ): PluginDownloader {
      val isFromCustomRepo = descriptor is PluginNode
      val downloadUrl = if (isFromCustomRepo) descriptor.downloadUrl else null
      return createDownloader(PluginUiModelAdapter(descriptor), host, buildNumber, downloadUrl, isFromCustomRepo)
    }

    @Throws(IOException::class)
    private fun createDownloader(
      descriptor: PluginUiModel,
      host: String?,
      buildNumber: BuildNumber?,
      downloadUrl: String?,
      isFromCustomRepo: Boolean,
    ): PluginDownloader {
      val url =
        if (isFromCustomRepo && host != null && downloadUrl != null) {
          toAbsoluteUrl(host, downloadUrl)
        }
        else {
          MarketplaceUrls.getPluginDownloadUrl(
            descriptor,
            getMarketplaceDownloadsUUID(),
            buildNumber,
            PluginManagerCore.getPlugin(descriptor.pluginId),
          )
        }
      return PluginDownloader(descriptor, url, buildNumber, ::showErrorDialog, null)
    }

    @Throws(IOException::class)
    private fun toAbsoluteUrl(host: String, downloadUrl: String): String {
      var actualDownloadUrl = downloadUrl
      if (actualDownloadUrl.indexOf(' ') >= 0) {
        actualDownloadUrl = actualDownloadUrl.replace(" ", "%20")  // a workaround for clumsily encoded custom plugin repositories
      }
      try {
        return URI(host).resolve(URI(actualDownloadUrl)).toASCIIString()
      }
      catch (e: URISyntaxException) {
        throw IOException("[$host, $actualDownloadUrl]", e)
      }
    }
  }
}
