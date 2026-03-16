// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.mac

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.SuggestedIde
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install.DownloadResult
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install.InstallationResult
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install.UltimateInstallationInfo
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install.UltimateInstaller
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install.runCommand
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.util.SystemProperties.getUserHome
import com.intellij.util.system.CpuArch
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

internal class MacOsInstaller(scope: CoroutineScope, project: Project) : UltimateInstaller(scope, project) {
  override val postfix = if (CpuArch.isArm64()) "-aarch64.dmg" else ".dmg"
  private val mountDirectory = updateTempDirectory.resolve("mount")

  override fun installUltimate(downloadResult: DownloadResult): InstallationResult? {
    val mountDir = mountDirectory.resolve(downloadResult.buildVersion)
    return try {
      mountDir.createDirectories()
      val command = GeneralCommandLine(
        "hdiutil", "attach", "-readonly", "-noautoopen", "-noautofsck", "-nobrowse", "-mountpoint",
        mountDir.pathString,
        downloadResult.downloadPath.pathString
      )

      if (!runCommand(command)) return null

      val app = mountDir.listDirectoryEntries().firstOrNull { entry -> entry.pathString.endsWith(".app") } ?: return null
      copyApp(app, downloadResult.suggestedIde)
    } finally {
      runDetach(mountDir.pathString)
      deleteInBackground(mountDir)
    }
  }

  @OptIn(ExperimentalPathApi::class)
  private fun copyApp(appPath: Path, suggestedIde: SuggestedIde): InstallationResult? {
    return try {
      val newAppPath = getUltimateInstallationDirectory()?.resolve(appPath.fileName) ?: return null
      appPath.copyToRecursively(newAppPath, followLinks = true, overwrite = true)
      InstallationResult(newAppPath, UltimateInstallationInfo(suggestedIde))
    } catch (e: Exception) {
      deleteInBackground(appPath)
      throw e
    }
  }

  private fun runDetach(mounterDirectory: String): Boolean {
    val detachCommand = GeneralCommandLine("hdiutil", "detach", "-force", mounterDirectory)
    return runCommand(detachCommand)
  }

  override fun startUltimate(installationResult: InstallationResult): Boolean {
   val start = GeneralCommandLine(
      "/usr/bin/open", "-n",
      installationResult.appPath.pathString,
      "--args", project.basePath, trialParameter
    )

    return runCommand(start)
  }

  override fun getUltimateInstallationDirectory(): Path? {
    return getUserHome().toNioPathOrNull()?.resolve("Applications")
  }
}