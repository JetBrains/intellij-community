// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.BuildInfo
import com.intellij.openapi.updateSettings.impl.ChannelStatus
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserService.Companion.ideaUltimate
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.linux.LinuxInstaller
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.mac.MacOsInstaller
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.windows.WindowsInstaller
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.indeterminateStep
import com.intellij.platform.util.progress.progressStep
import com.intellij.platform.util.progress.withRawProgressReporter
import com.intellij.util.io.HttpRequests
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

private const val TOOLBOX_INSTALL_BASE_URL = "http://localhost:52829/install/IDEA-U"
private const val TOOLBOX_ORIGIN = "https://toolbox.app"
internal const val BASE_ULTIMATE_DOWNLOAD_URL = "https://download.jetbrains.com/idea/ideaIU"

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
      current == OS.macOS -> MacOsInstaller(coroutineScope)
      current == OS.Windows && !CpuArch.isArm64() -> WindowsInstaller(coroutineScope)
      current == OS.Linux -> LinuxInstaller(coroutineScope)
      else -> null
    }
  }

  fun install(pluginId: PluginId? = null) {
    coroutineScope.launch {
      try {
        installerLock.withLock {
          withBackgroundProgress(project, IdeBundle.message("plugins.advertiser.ultimate.upgrade"), true) {
            FUSEventSource.EDITOR.logTryUltimate(project, pluginId)

            val productData = UpdateChecker.loadProductData(null)
            val build = productData?.channels?.firstOrNull { it.status == ChannelStatus.RELEASE }?.builds?.first()
                        ?: return@withBackgroundProgress

            //val result = tryToInstallViaToolbox(build)
            //if (result) return@withBackgroundProgress

            if (!tryToInstall(build, pluginId)) {
              useFallback(pluginId)
            }
          }
        }
      } catch (e: Exception) {
        when {
          e is CancellationException -> {
            FUSEventSource.EDITOR.logUpgradeToUltimateCancelled(project, pluginId)
            throw e
          }
          else -> useFallback(pluginId)
        }
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

  private suspend fun tryToInstall(buildInfo: BuildInfo, pluginId: PluginId? = null): Boolean {
    if (installer == null) return false

    FUSEventSource.EDITOR.logUltimateDownloadStarted(project, pluginId)
    val result = downloadStep(buildInfo)
      ?.let { downloadResult ->
        FUSEventSource.EDITOR.logUltimateInstallationStarted(project, pluginId)
        indeterminateStep(IdeBundle.message("plugins.advertiser.ultimate.install")) { installer?.install(downloadResult) }
      }
      ?.let { installResult ->
        FUSEventSource.EDITOR.logInstalledUltimateOpened(project, pluginId)
        installer!!.startUltimate(installResult)
      }

    return result != null && result
  }

  private suspend fun downloadStep(buildInfo: BuildInfo): DownloadResult? {
    return progressStep(1.0, text = IdeBundle.message("plugins.advertiser.ultimate.download")) {
      withRawProgressReporter {
        coroutineToIndicator {
          installer?.download(buildInfo, ProgressManager.getInstance().progressIndicator)
        }
      }
    }
  }

  private fun useFallback(pluginId: PluginId? = null) {
    FUSEventSource.EDITOR.logUltimateFallbackUsed(project, ideaUltimate.defaultDownloadUrl, pluginId)
  }
}

internal abstract class UltimateInstaller(val scope: CoroutineScope) {
  abstract val postfix: String

  protected val updateTempDirectory: Path = Path.of(PathManager.getTempPath(), "ultimate-upgrade")

  fun download(buildInfo: BuildInfo, indicator: ProgressIndicator): DownloadResult {
    val link = generateDownloadLink(buildInfo, postfix)
    val downloadPath = updateTempDirectory.resolve("${buildInfo.version}$postfix")

    if (!downloadPath.exists()) {
      try {
        HttpRequests.request(link).saveToFile(downloadPath.toFile(), indicator)
      } catch (e: Exception) {
        deleteInBackground(downloadPath)
        throw e
      }
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

internal fun generateDownloadLink(buildInfo: BuildInfo, postfix: String): String {
  return "$BASE_ULTIMATE_DOWNLOAD_URL-${buildInfo.version}$postfix"
}

internal data class DownloadResult(val downloadPath: Path, val buildVersion: String)
internal data class InstallationResult(val appPath: Path)