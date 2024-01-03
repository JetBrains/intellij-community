// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.windows

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.*
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.windows.WindowsInstallationType.INSTALLER
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.windows.WindowsInstallationType.ZIP
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.util.SystemProperties
import com.intellij.util.io.Decompressor
import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32Util
import java.io.File
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

internal class WindowsInstaller : UltimateInstaller() {
  private val installationType = getInstallationType()

  override val postfix = when (installationType) {
    ZIP -> ".win.zip"
    INSTALLER -> ".exe"
  }

  override fun install(downloadResult: DownloadResult): InstallationResult? {
    return when (installationType) {
      ZIP -> installFromZip(downloadResult)
      INSTALLER -> installFromExe(downloadResult)
    }
  }

  private fun installFromExe(downloadResult: DownloadResult): InstallationResult? {
    val path = downloadResult.downloadPath
    val installationPath = provideInstallationPath(downloadResult.buildVersion) ?: return null

    val command = GeneralCommandLine("cmd.exe", "/c")
      .withParameters("$path /S /D=$installationPath")

    val result = runCommand(command)
    if (!result) {
      ultimateInstallationLogger.warn("Could not execute command: $command")
      return null
    }

    return InstallationResult(installationPath)
  }

  private fun provideInstallationPath(version: String): Path? {
    val name = "IntelliJ IDEA $version"
    val path = getUltimateInstallationDirectory()

    return "$path${File.separator}$name".toNioPathOrNull()
  }

  private fun installFromZip(downloadResult: DownloadResult): InstallationResult? {
    val path = downloadResult.downloadPath

    val installationPath = provideInstallationPath(downloadResult.buildVersion) ?: return null
    Decompressor.Zip(path).extract(installationPath)

    return InstallationResult(installationPath)
  }

  override fun startUltimate(installationResult: InstallationResult): Boolean {
    val appPath = installationResult.appPath
    val exePath = appPath.resolve("bin").resolve("idea64.exe").pathString
    val command = GeneralCommandLine().withExePath(exePath)

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

private fun getInstallationType(): WindowsInstallationType {
  val binPath = PathManager.getBinPath().toNioPathOrNull() ?: return INSTALLER

  return if (binPath.listDirectoryEntries().any { it.startsWith("Uninstall") }) INSTALLER else ZIP
}

private enum class WindowsInstallationType {
  INSTALLER, ZIP
}