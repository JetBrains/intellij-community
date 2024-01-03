// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.mac

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.DownloadResult
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.InstallationResult
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.UltimateInstaller
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.runCommand
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.util.SystemProperties.getUserHome
import com.intellij.util.system.CpuArch
import java.nio.file.Path
import kotlin.io.path.*

internal class MacOsInstaller : UltimateInstaller() {
  override val postfix = if (CpuArch.isArm64()) "-aarch64.dmg" else ".dmg"
  private val mountDirectory = updateTempDirectory.resolve("mount")

  @OptIn(ExperimentalPathApi::class)
  override fun install(downloadResult: DownloadResult): InstallationResult? {
    val dmg = downloadResult.downloadPath

    val mountDir = mountDirectory.resolve(downloadResult.buildVersion)
    mountDir.createDirectories()

    val command = GeneralCommandLine(
      "hdiutil", "attach", "-readonly", "-noautoopen", "-noautofsck", "-nobrowse", "-mountpoint",
      mountDir.pathString,
      dmg.pathString
    )

    if (!runCommand(command)) return null

    val app = mountDir.listDirectoryEntries().firstOrNull { entry -> entry.pathString.endsWith(".app") } ?: return null

    val newAppPath = getUltimateInstallationDirectory()?.resolve(app.fileName) ?: return null
    app.copyToRecursively(newAppPath, followLinks = true, overwrite = true)

    runDetach(mountDir.pathString)

    return InstallationResult(newAppPath)
  }

  private fun runDetach(mounterDirectory: String): Boolean {
    val detachCommand = GeneralCommandLine("hdiutil", "detach", "-force", mounterDirectory)
    return runCommand(detachCommand)
  }

  override fun startUltimate(installationResult: InstallationResult): Boolean {
    val start = GeneralCommandLine(
      "/usr/bin/open", "-n",
      installationResult.appPath.pathString,
    )

    return runCommand(start)
  }

  override fun getUltimateInstallationDirectory(): Path? {
    return getUserHome().toNioPathOrNull()?.resolve("Applications")
  }
}