// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.updateSettings.impl.BuildInfo
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.SuggestedIde
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.io.HttpRequests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

internal abstract class UltimateInstaller(
  private val scope: CoroutineScope,
  protected val project: Project,
) {
  abstract val postfix: String

  protected val updateTempDirectory: Path = Path.of(PathManager.getTempPath(), "ultimate-upgrade")
  protected val trialParameter = "-Drequest.trial=true"

  fun download(link: String, buildInfo: BuildInfo, indicator: ProgressIndicator, suggestedIde: SuggestedIde): DownloadResult {
    showHint(IdeBundle.message("plugins.advertiser.try.ultimate.download.started.balloon", suggestedIde.name))
    val buildNumber = buildInfo.number.asString()
    val downloadPath = updateTempDirectory.resolve("$buildNumber$postfix")

    try {
      HttpRequests.request(link).saveToFile(downloadPath.toFile(), indicator)
    }
    catch (e: Exception) {
      deleteInBackground(downloadPath)
      throw e
    }

    return DownloadResult(downloadPath, buildInfo.version, suggestedIde)
  }

  fun install(downloadResult: DownloadResult): InstallationResult? {
    return try {
      installUltimate(downloadResult)
    }
    finally {
      deleteInBackground(downloadResult.downloadPath)
    }
  }

  fun generateDownloadLink(buildInfo: BuildInfo, suggestedIde: SuggestedIde): String {
    val version = if (Registry.`is`("ide.try.ultimate.use.eap")) buildInfo.number.asStringWithoutProductCodeAndSnapshot() else buildInfo.version
    return "${suggestedIde.baseDownloadUrl}-$version$postfix"
  }

  fun notifyAndOfferStart(installationResult: InstallationResult, suggestedIde: SuggestedIde, pluginId: PluginId?) {
    val currentIde = ApplicationInfo.getInstance().fullApplicationName

    val notification = NotificationGroupManager.getInstance().getNotificationGroup("Ultimate Installed")
      .createNotification(
        IdeBundle.message("notification.group.advertiser.try.ultimate.installed.title", suggestedIde.name),
        IdeBundle.message("notification.group.advertiser.try.ultimate.installed.content", currentIde),
        NotificationType.INFORMATION
      )
      .setSuggestionType(true)
      .addAction(object : NotificationAction(IdeBundle.messagePointer("action.Anonymous.text.start.trial")) {
        override fun actionPerformed(e: AnActionEvent, notification: com.intellij.notification.Notification) {
          scope.launch {
            val openActivity = FUSEventSource.EDITOR.logTryUltimateIdeOpened(project, pluginId)
            val started = startUltimate(installationResult)
            if (started) {
              openActivity.finished()

              val application = ApplicationManager.getApplication()
              application.invokeLater { application.exit(true, true, false) }
            }
          }
        }
      })
    
    notification.setIcon(getIcon(suggestedIde))
    notification.notify(project)
  }

  abstract fun installUltimate(downloadResult: DownloadResult): InstallationResult?

  abstract fun startUltimate(installationResult: InstallationResult): Boolean

  abstract fun getUltimateInstallationDirectory(): Path?

  @OptIn(ExperimentalPathApi::class)
  protected fun deleteInBackground(directory: Path) {
    val result = scope.runCatching { directory.deleteRecursively() }
    if (result.isFailure) {
      logger<UltimateInstallationService>().warn("Could not clear directories: ${result.exceptionOrNull()?.suppressedExceptions}")
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

    logger<UltimateInstallationService>().warn(output.stderr)
    false
  }
  catch (e: Exception) {
    logger<UltimateInstallationService>().warn(e)
    false
  }
}

internal data class DownloadResult(
  val downloadPath: Path,
  val buildVersion: String,
  val suggestedIde: SuggestedIde,
)

internal data class InstallationResult(
  val appPath: Path,
  val installationInfo: UltimateInstallationInfo
)

open class UltimateInstallationInfo(
  val suggestedIde: SuggestedIde,
)
