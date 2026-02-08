// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.linux

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil.loadTemplate
import com.intellij.ide.actions.CreateDesktopEntryAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.SuggestedIde
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install.DownloadResult
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install.InstallationResult
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install.UltimateInstallationInfo
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install.UltimateInstaller
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install.isPycharmProfessional
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install.runCommand
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.util.SystemProperties
import com.intellij.util.asSafely
import com.intellij.util.io.Decompressor
import com.intellij.util.io.write
import com.intellij.util.system.CpuArch
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.pathString

internal class LinuxInstaller(scope: CoroutineScope, project: Project) : UltimateInstaller(scope, project) {
  override val postfix = if (CpuArch.isArm64()) "-aarch64.tar.gz" else ".tar.gz"

  override fun installUltimate(downloadResult: DownloadResult): InstallationResult? {
    val installPath = getUltimateInstallationDirectory() ?: return null
    val entries = mutableListOf<Path>()

    try {
      val decompressor = Decompressor.Tar(downloadResult.downloadPath)
      decompressor.postProcessor(entries::add).extract(installPath)
    }
    catch (e: Exception) {
      deleteInBackground(installPath)
      throw e
    }

    val suggestedIde = downloadResult.suggestedIde
    val installFolder = installPath.resolve(entries.first().fileName)
    val installationInfo = getInstallationInfo(installFolder, suggestedIde) ?: return null

    createDesktopEntry(installationInfo)

    return InstallationResult(installFolder, installationInfo)
  }

  override fun startUltimate(installationResult: InstallationResult): Boolean {
    val installationInfo = installationResult.installationInfo.asSafely<LinuxInstallationInfo>() ?: return false
    val command = GeneralCommandLine("sh", "-c", "${installationInfo.scriptPath.pathString} ${project.basePath} $trialParameter &")

    return runCommand(command)
  }

  private fun createDesktopEntry(installationInfo: LinuxInstallationInfo) {
    val suggestedIde = installationInfo.suggestedIde
    val ideName = suggestedIde.name
    val desktopFile = getLocalShareDir()?.resolve("applications")?.resolve("${ideName}.desktop") ?: return
    if (!desktopFile.exists()) {
      desktopFile.createFile()
    }

    val entries = mutableMapOf(
      "\$NAME\$" to ideName,
      "\$SCRIPT\$" to installationInfo.scriptPath.pathString,
      "\$WM_CLASS\$" to installationInfo.wmClass,
      "\$COMMENT\$" to suggestedIde.productCode,
    )

    installationInfo.iconPath?.let { entries.put("\$ICON\$", it.pathString) }
    val content = loadTemplate(CreateDesktopEntryAction::class.java.classLoader, "entry.desktop", entries)

    desktopFile.write(content)
  }

  private fun getInstallationInfo(installPath: Path, suggestedIde: SuggestedIde): LinuxInstallationInfo? {
    val ide = if (suggestedIde.isPycharmProfessional()) "pycharm" else "idea"

    val bin = installPath.resolve("bin")
    val script = bin.resolve("$ide.sh")
    if (script.notExists()) return null

    val icon = bin.resolve("$ide.svg").takeIf { it.exists() }

    return LinuxInstallationInfo(suggestedIde, script, icon, "jetbrains-$ide")
  }

  override fun getUltimateInstallationDirectory(): Path? {
    val localAppPath = System.getenv("XDG_DATA_HOME")?.toNioPathOrNull() ?: getLocalShareDir()
    return localAppPath?.resolve("JetBrains")
  }

  private fun getLocalShareDir(): Path? {
    return SystemProperties.getUserHome().toNioPathOrNull()?.resolve(".local/share")
  }
}

internal class LinuxInstallationInfo(
  suggestedIde: SuggestedIde,
  val scriptPath: Path,
  val iconPath: Path?,
  val wmClass: String,
) : UltimateInstallationInfo(suggestedIde)