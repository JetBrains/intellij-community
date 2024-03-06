// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.windows

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install.DownloadResult
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install.InstallationResult
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install.UltimateInstaller
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install.runCommand
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.util.SystemProperties
import com.intellij.util.applyIf
import com.intellij.util.io.delete
import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32Util
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString

internal class WindowsInstaller(scope: CoroutineScope, project: Project) : UltimateInstaller(scope, project) {
  override val postfix = ".exe"

  override fun installUltimate(downloadResult: DownloadResult): InstallationResult? {
    val path = downloadResult.downloadPath
    val installationPath = provideInstallationPath(downloadResult) ?: return null

    val command = GeneralCommandLine("cmd.exe", "/c").withParameters("$path /S /D=$installationPath")

    val result = runCommand(command)
    if (!result) {
      downloadResult.downloadPath.delete(true)
      return null
    }

    return InstallationResult(installationPath, downloadResult.suggestedIde)
  }

  private fun provideInstallationPath(downloadResult: DownloadResult): Path? {
    val version = downloadResult.buildVersion
    val name = "${downloadResult.suggestedIde.name} $version"
    val path = getUltimateInstallationDirectory()

    return "$path${File.separator}$name".toNioPathOrNull()
  }

  override fun startUltimate(installationResult: InstallationResult): Boolean {
    val appPath = installationResult.appPath
    val exeName = if (installationResult.suggestedIde.productCode == "PY") "pycharm64" else "idea64"
    val exePath = appPath.resolve("bin").resolve("$exeName.exe").pathString

    val basePath = project.basePath
    val parameters = mutableListOf("", exePath).applyIf(basePath != null) {
      add(basePath!!)
      this
    }
    parameters.add(trialParameter)
    
    val command = GeneralCommandLine("cmd", "/c", "start").withParameters(parameters)

    return runCommand(command)
  }

  override fun getUltimateInstallationDirectory(): Path? {
    return try {
      Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_UserProgramFiles)?.toNioPathOrNull()
    } catch (e: Exception) {
      val localAppData = Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_LocalAppData).toNioPathOrNull()
                         ?: SystemProperties.getUserHome().toNioPathOrNull()?.resolve("AppData/Local")
      localAppData?.resolve("Programs")
    }
  }
}