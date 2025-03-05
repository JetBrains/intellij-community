// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.messages.MessagesService
import com.intellij.openapi.updateSettings.impl.BuildInfo
import com.intellij.openapi.updateSettings.impl.ChannelStatus
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.SuggestedIde
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.disableTryUltimate
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.enableTryUltimate
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.linux.LinuxInstaller
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.mac.MacOsInstaller
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.windows.WindowsInstaller
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.ui.EditorNotifications
import com.intellij.util.PlatformUtils
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.Nls
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.swing.Icon

private const val TOOLBOX_INSTALL_BASE_URL: String = "http://localhost:52829/install/IDEA-U"
private const val TOOLBOX_ORIGIN: String = "https://toolbox.app"

@Service(Service.Level.PROJECT)
class UltimateInstallationService(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
  private val installerLock = Mutex()

  private val installer: UltimateInstaller? by lazy {
    val current = OS.CURRENT
    when {
      current == OS.macOS -> MacOsInstaller(coroutineScope, project)
      current == OS.Windows && !CpuArch.isArm64() -> WindowsInstaller(coroutineScope, project)
      current == OS.Linux -> LinuxInstaller(coroutineScope, project)
      else -> null
    }
  }

  fun install(pluginId: PluginId? = null, suggestedIde: SuggestedIde, eventSource: FUSEventSource) {
    if (!canBeAutoInstalled(suggestedIde)) {
      eventSource.openDownloadPageAndLog(project = project,
                                         url = suggestedIde.defaultDownloadUrl,
                                         suggestedIde = suggestedIde,
                                         pluginId = pluginId)
      return
    }

    coroutineScope.launch {
      try {
        installerLock.withLock {
          withBackgroundProgress(project, IdeBundle.message("plugins.advertiser.try.ultimate.upgrade", suggestedIde.name), true) {
            val productData = UpdateChecker.loadProductData(null)
            val status = if (Registry.`is`("ide.try.ultimate.use.eap")) ChannelStatus.EAP else ChannelStatus.RELEASE
            val build = productData?.channels?.firstOrNull { it.status == status }?.builds?.first() ?: return@withBackgroundProgress

            disableTryUltimate(project)
            val isInstalled = tryToInstall(suggestedIde, build, pluginId)
            if (!isInstalled) {
              enableTryUltimate(project)
            }
          }
        }
      }
      catch (e: CancellationException) {
        FUSEventSource.EDITOR.logTryUltimateCancelled(project, pluginId)
        enableTryUltimate(project)
        throw e
      }
      finally {
        EditorNotifications.getInstance(project).updateAllNotifications()
      }
    }
  }

  private suspend fun tryToInstall(suggestedIde: SuggestedIde, build: BuildInfo, pluginId: PluginId?): Boolean {
    if (Registry.`is`("ide.try.ultimate.automatic.installation.use.toolbox")) {
      val result = tryToInstallViaToolbox(build)
      if (result) {
        FUSEventSource.EDITOR.logTryUltimateToolboxUsed(project, pluginId)
        return true
      }
    }

    return tryToInstall(build, pluginId, suggestedIde)
  }

  private fun tryToInstallViaToolbox(buildInfo: BuildInfo): Boolean {
    val build = buildInfo.number.components.joinToString(".")
    val uri = URI.create("$TOOLBOX_INSTALL_BASE_URL/$build")
    val request = HttpRequest.newBuilder().uri(uri).header("Origin", TOOLBOX_ORIGIN).build()
    val resp = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())

    return resp.statusCode() == 200
  }

  private suspend fun tryToInstall(buildInfo: BuildInfo, pluginId: PluginId? = null, suggestedIde: SuggestedIde): Boolean =
    reportSequentialProgress { reporter ->
      if (installer == null) return@reportSequentialProgress false

      val downloadResult = reporter.nextStep(endFraction = 100, IdeBundle.message("plugins.advertiser.try.ultimate.download")) {
        download(buildInfo, suggestedIde, pluginId)
      } ?: return@reportSequentialProgress false

      val installationResult = reporter.indeterminateStep(IdeBundle.message("plugins.advertiser.try.ultimate.install")) {
        installIde(downloadResult, suggestedIde, pluginId)
      } ?: return@reportSequentialProgress false

      reporter.indeterminateStep(IdeBundle.message("plugins.advertiser.try.ultimate.opening", suggestedIde.name)) {
        start(installationResult, suggestedIde, pluginId)
      }
    }

  private suspend fun download(buildInfo: BuildInfo, suggestedIde: SuggestedIde, pluginId: PluginId?): DownloadResult? {
    val link = installer?.generateDownloadLink(buildInfo, suggestedIde) ?: return null
    val result = runCatching {
      val downloadActivity = FUSEventSource.EDITOR.logTryUltimateDownloadStarted(project, pluginId)
      val result = coroutineToIndicator {
        installer?.download(link, buildInfo, ProgressManager.getInstance().progressIndicator, suggestedIde)
      }
      downloadActivity.finished()
      result
    }.getOrNullLogged()

    if (result == null) {
      val downloadMessage = IdeBundle.message("plugins.advertiser.try.ultimate.could.not.download", suggestedIde.name, link)
      showProcessErrorDialogWithRetry(downloadMessage, pluginId, suggestedIde) { download(buildInfo, suggestedIde, pluginId) }
    }

    return result
  }

  private suspend fun start(installationResult: InstallationResult, suggestedIde: SuggestedIde, pluginId: PluginId?): Boolean {
    val startResult = runCatching { installer!!.notifyAndOfferStart(installationResult, suggestedIde, pluginId) }
    if (startResult.isFailure) {
      val openMessage = IdeBundle.message("plugins.advertiser.try.ultimate.could.not.open", suggestedIde.name)
      showOpenProcessErrorDialog(openMessage, pluginId, suggestedIde)
    }

    return startResult.isSuccess
  }

  private fun <T> Result<T>.getOrNullLogged(): T? {
    if (isFailure) {
      val exception = exceptionOrNull()
      logger<UltimateInstallationService>().warn("Exception while trying upgrade to Ultimate: ${exception?.message}")
      if (exception is CancellationException) throw exception
    }

    return getOrNull()
  }

  private suspend fun installIde(downloadResult: DownloadResult, suggestedIde: SuggestedIde, pluginId: PluginId?): InstallationResult? {
    val installResult = runCatching {
      val installActivity = FUSEventSource.EDITOR.logTryUltimateInstallationStarted(project, pluginId)
      val installResult = installer?.install(downloadResult)
      installActivity.finished()
      installResult
    }.getOrNullLogged()

    if (installResult == null) {
      val installMessage = IdeBundle.message("plugins.advertiser.try.ultimate.could.not.install", suggestedIde.name)
      showProcessErrorDialogWithRetry(installMessage, pluginId, suggestedIde) { installIde(downloadResult, suggestedIde, pluginId) }
    }

    return installResult
  }

  private suspend fun showProcessErrorDialogWithRetry(message: @Nls String, pluginId: PluginId?, suggestedIde: SuggestedIde, retryFunc: suspend () -> Unit) {
    val dialogResult = messageDialog(message, suggestedIde.name,
                                     listOf(IdeBundle.message("plugins.advertiser.try.ultimate.dialog.try.again"),
                                            IdeBundle.message("plugins.advertiser.try.ultimate.dialog.open.website"),
                                            IdeBundle.message("plugins.advertiser.try.ultimate.cancel.button")), Messages.getErrorIcon())

    when (dialogResult) {
      0 -> retryFunc.invoke()
      1 -> useFallback(pluginId = pluginId, defaultDownloadUrl = suggestedIde.defaultDownloadUrl)
      else -> Unit
    }
  }

  private suspend fun showOpenProcessErrorDialog(message: @Nls String, pluginId: PluginId?, suggestedIde: SuggestedIde) {
    val result = messageDialog(message, suggestedIde.name,
                               listOf(IdeBundle.message("plugins.advertiser.try.ultimate.dialog.open.website"),
                                      IdeBundle.message("plugins.advertiser.try.ultimate.cancel.button")), Messages.getErrorIcon())
    when (result) {
      0 -> useFallback(pluginId = pluginId, defaultDownloadUrl = suggestedIde.defaultDownloadUrl)
      else -> Unit
    }
  }

  private suspend fun messageDialog(message: @Nls String, suggestedIdeName: String, options: List<@Nls String>, icon: Icon): Int {
    return withContext(Dispatchers.EDT) {
      MessagesService.getInstance().showMessageDialog(
        project,
        title = IdeBundle.message("plugins.advertiser.try.ultimate.dialog.title", suggestedIdeName),
        message = message,
        options = options.toTypedArray(),
        focusedOptionIndex = 0,
        doNotAskOption = null,
        icon = icon
      )
    }
  }

  private fun useFallback(pluginId: PluginId? = null, defaultDownloadUrl: String) {
    FUSEventSource.EDITOR.logTryUltimateFallback(project, defaultDownloadUrl, pluginId)
  }
}

private fun canBeAutoInstalled(suggestedIde: SuggestedIde): Boolean {
  return when {
    isIdea(suggestedIde) -> true
    isPycharm(suggestedIde) -> true
    else -> false
  }
}

private fun isPycharm(suggestedIde: SuggestedIde) = PlatformUtils.isPyCharmCommunity() && suggestedIde.isPycharmProfessional()
private fun isIdea(suggestedIde: SuggestedIde) = PlatformUtils.isIdeaCommunity() && suggestedIde.isIdeUltimate()

internal fun getIcon(suggestedIde: SuggestedIde): Icon? {
  return when {
    isIdea(suggestedIde) -> AllIcons.Ultimate.IdeaUltimatePromo
    isPycharm(suggestedIde) -> AllIcons.Ultimate.PycharmPromo
    else -> null
  }
}

internal fun SuggestedIde.isIdeUltimate() = productCode == "IU"
internal fun SuggestedIde.isPycharmProfessional() = productCode == "PY"