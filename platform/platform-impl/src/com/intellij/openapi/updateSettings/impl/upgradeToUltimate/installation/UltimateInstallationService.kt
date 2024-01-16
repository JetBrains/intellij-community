// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.messages.MessageDialog
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.updateSettings.impl.BuildInfo
import com.intellij.openapi.updateSettings.impl.ChannelStatus
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.SuggestedIde
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.linux.LinuxInstaller
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.mac.MacOsInstaller
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.windows.WindowsInstaller
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.indeterminateStep
import com.intellij.platform.util.progress.progressStep
import com.intellij.platform.util.progress.withRawProgressReporter
import com.intellij.ui.EditorNotifications
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.PlatformUtils.isIdeaCommunity
import com.intellij.util.PlatformUtils.isPyCharmCommunity
import com.intellij.util.io.HttpRequests
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.Nls
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

private const val TOOLBOX_INSTALL_BASE_URL = "http://localhost:52829/install/IDEA-U"
private const val TOOLBOX_ORIGIN = "https://toolbox.app"

private val ultimateInstallationLogger = logger<UltimateInstallationService>()

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

  fun install(pluginId: PluginId? = null, suggestedIde: SuggestedIde) {
    coroutineScope.launch {
      try {
        installerLock.withLock {
          withBackgroundProgress(project, IdeBundle.message("plugins.advertiser.try.ultimate.upgrade"), true) {
            if (!suggestedIde.canBeAutoInstalled()) {
              useFallback(pluginId, suggestedIde.defaultDownloadUrl)
              return@withBackgroundProgress
            }

            val productData = UpdateChecker.loadProductData(null)
            val build = productData?.channels?.firstOrNull { it.status == ChannelStatus.RELEASE }?.builds?.first()
                        ?: return@withBackgroundProgress

            if (Registry.`is`("ide.try.ultimate.automatic.installation.use.toolbox")) {
              val result = tryToInstallViaToolbox(build)
              if (result) {
                FUSEventSource.EDITOR.logTryUltimateToolboxUsed(project, pluginId)
                return@withBackgroundProgress
              }
            }

            tryToInstall(build, pluginId, suggestedIde)
          }
        }
      }
      catch (e: CancellationException) {
        FUSEventSource.EDITOR.logTryUltimateCancelled(project, pluginId)
        throw e
      }
      finally {
        EditorNotifications.getInstance(project).updateAllNotifications()
      }
    }
  }

  private fun tryToInstallViaToolbox(buildInfo: BuildInfo): Boolean {
    val build = buildInfo.number.components.joinToString(".")
    val uri = URI.create("$TOOLBOX_INSTALL_BASE_URL/$build")
    val request = HttpRequest.newBuilder().uri(uri).header("Origin", TOOLBOX_ORIGIN).build()
    val resp = HttpClient.newHttpClient().send(request, BodyHandlers.ofString())

    return resp.statusCode() == 200
  }

  private suspend fun tryToInstall(buildInfo: BuildInfo, pluginId: PluginId? = null, suggestedIde: SuggestedIde) {
    if (installer == null) return

    val downloadResult = runCatching { download(buildInfo, suggestedIde, pluginId) }.getOrNullLogged()
    if (downloadResult == null) {
      showProcessErrorDialog("Download", pluginId, suggestedIde)
      return
    }

    val installationResult = runCatching { installIde(downloadResult, pluginId) }.getOrNullLogged()
    if (installationResult == null) {
      showProcessErrorDialog("Install", pluginId, suggestedIde)
      return
    }

    val startResult = runCatching {
      val openActivity = FUSEventSource.EDITOR.logTryUltimateIdeOpened(project, pluginId)
      indeterminateStep(IdeBundle.message("plugins.advertiser.try.ultimate.opening", suggestedIde.name)) {
        installer!!.startUltimateAndNotify(installationResult, suggestedIde)
      }
      openActivity.finished()
    }

    if (startResult.isFailure) {
      showOpenProcessErrorDialog("Open", pluginId, suggestedIde)
    }
  }

  private suspend fun download(buildInfo: BuildInfo, suggestedIde: SuggestedIde, pluginId: PluginId?): DownloadResult? {
    val downloadActivity = FUSEventSource.EDITOR.logTryUltimateDownloadStarted(project, pluginId)
    val result = progressStep(1.0, text = IdeBundle.message("plugins.advertiser.try.ultimate.download")) {
      withRawProgressReporter {
        coroutineToIndicator {
          installer?.download(buildInfo, ProgressManager.getInstance().progressIndicator, suggestedIde)
        }
      }
    }
    downloadActivity.finished()
    return result
  }

  private fun <T> Result<T>.getOrNullLogged(): T? {
    if (isFailure) {
      val exception = exceptionOrNull()
      ultimateInstallationLogger.warn("Exception while trying upgrade to Ultimate: ${exception?.message}")
      if (exception is CancellationException) throw exception
    }

    return getOrNull()
  }

  private suspend fun installIde(downloadResult: DownloadResult, pluginId: PluginId?): InstallationResult? {
    val installActivity = FUSEventSource.EDITOR.logTryUltimateInstallationStarted(project, pluginId)
    val installResult = indeterminateStep(IdeBundle.message("plugins.advertiser.try.ultimate.install")) {
      installer?.install(downloadResult)
    }
    installActivity.finished()
    return installResult
  }

  private suspend fun showProcessErrorDialog(stepName: String, pluginId: PluginId?, suggestedIde: SuggestedIde) {
    val dialog = messageDialog(stepName, listOf("Try Again", "Open Website", "Cancel"))
    when (dialog.exitCode) {
      0 -> install(pluginId, suggestedIde)
      1 -> useFallback(pluginId = pluginId, defaultDownloadUrl = suggestedIde.defaultDownloadUrl)
      else -> Unit
    }
  }

  private suspend fun showOpenProcessErrorDialog(stepName: String, pluginId: PluginId?, suggestedIde: SuggestedIde) {
    val dialog = messageDialog(stepName, listOf("Open Website", "Cancel"))
    when (dialog.exitCode) {
      0 -> useFallback(pluginId = pluginId, defaultDownloadUrl = suggestedIde.defaultDownloadUrl)
      else -> Unit
    }
  }

  private suspend fun messageDialog(stepName: String, options: List<String>): MessageDialog {
    return withContext(Dispatchers.EDT) {
      val dialog = MessageDialog(
        project,
        IdeBundle.message("plugins.advertiser.try.ultimate.dialog.step.failed", stepName),
        IdeBundle.message("plugins.advertiser.try.ultimate.dialog.title"),
        options.toTypedArray(),
        -1,
        AllIcons.General.Error,
        false
      )
      dialog.show()
      dialog
    }
  }

  private fun useFallback(pluginId: PluginId? = null, defaultDownloadUrl: String) {
    FUSEventSource.EDITOR.logTryUltimateFallback(project, defaultDownloadUrl, pluginId)
  }
}

internal abstract class UltimateInstaller(
  private val scope: CoroutineScope,
  private val project: Project,
) {
  abstract val postfix: String

  protected val updateTempDirectory: Path = Path.of(PathManager.getTempPath(), "ultimate-upgrade")

  fun download(buildInfo: BuildInfo, indicator: ProgressIndicator, suggestedIde: SuggestedIde): DownloadResult {
    showHint(IdeBundle.message("plugins.advertiser.try.ultimate.download.started.balloon", suggestedIde.name))

    val link = generateDownloadLink(buildInfo, suggestedIde, postfix)
    val downloadPath = updateTempDirectory.resolve("${buildInfo.version}$postfix")

    try {
      HttpRequests.request(link).saveToFile(downloadPath.toFile(), indicator)
    } catch (e: Exception) {
      deleteInBackground(downloadPath)
      throw e
    }

    return DownloadResult(downloadPath, buildInfo.version)
  }

  fun install(downloadResult: DownloadResult): InstallationResult? {
    return try {
      installUltimate(downloadResult)
    } finally {
      deleteInBackground(downloadResult.downloadPath)
    }
  }

  fun startUltimateAndNotify(installationResult: InstallationResult, suggestedIde: SuggestedIde): Boolean {
    val notification = NotificationGroupManager.getInstance().getNotificationGroup("Ultimate Installed")
      .createNotification(
        IdeBundle.message("notification.group.advertiser.try.ultimate.installed"),
        IdeBundle.message("notification.plugin.advertiser.try.ultimate.started", suggestedIde.name),
        NotificationType.INFORMATION
      )
      .setSuggestionType(true)
      .addAction(object : NotificationAction(IdeBundle.messagePointer("action.Anonymous.text.close.ide")) {
        override fun actionPerformed(e: AnActionEvent, notification: com.intellij.notification.Notification) {
          ApplicationManager.getApplication().exit()
        }
      })

    notification.notify(project)

    return startUltimate(installationResult)
  }

  abstract fun installUltimate(downloadResult: DownloadResult): InstallationResult?

  abstract fun startUltimate(installationResult: InstallationResult): Boolean

  abstract fun getUltimateInstallationDirectory(): Path?

  @OptIn(ExperimentalPathApi::class)
  protected fun deleteInBackground(directory: Path) {
    val result = scope.runCatching { directory.deleteRecursively() }
    if (result.isFailure) {
      ultimateInstallationLogger.warn("Could not clear directories: ${result.exceptionOrNull()?.suppressedExceptions}")
    }
  }

  private fun showHint(text: @Nls String) {
    val statusBarComponent = WindowManager.getInstance().getStatusBar(project).component ?: return
    JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(text, MessageType.INFO, null)
      .setFadeoutTime(5000)
      .createBalloon()
      .show(RelativePoint.getNorthEastOf(statusBarComponent), Balloon.Position.atRight)
  }
}

internal fun runCommand(command: GeneralCommandLine): Boolean {
  return try {
    val output = ExecUtil.execAndGetOutput(command)
    if (output.exitCode == 0) return true

    ultimateInstallationLogger.warn(output.stderr)
    false
  } catch (e: Exception) {
    ultimateInstallationLogger.warn(e)
    false
  }
}

private fun generateDownloadLink(buildInfo: BuildInfo, suggestedIde: SuggestedIde, postfix: String): String {
  return "${suggestedIde.baseDownloadUrl}-${buildInfo.version}$postfix"
}

internal data class DownloadResult(val downloadPath: Path, val buildVersion: String)
internal data class InstallationResult(val appPath: Path)

private fun SuggestedIde.canBeAutoInstalled(): Boolean {
  return when {
    isIdeaCommunity() && productCode == "IU" -> true
    isPyCharmCommunity() && productCode == "PY" -> true
    else -> false
  }
}