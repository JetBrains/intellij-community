// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.linux

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.install.*
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.util.SystemProperties
import com.intellij.util.io.Decompressor
import com.intellij.util.system.CpuArch
import kotlinx.coroutines.CoroutineScope
import java.nio.file.Path
import kotlin.io.path.pathString

internal class LinuxInstaller(scope: CoroutineScope, project: Project) : UltimateInstaller(scope, project) {
  override val postfix = if (CpuArch.isArm64()) "-aarch64.tar.gz" else ".tar.gz"

  override fun installUltimate(downloadResult: DownloadResult): InstallationResult? {
    val installationPath = getUltimateInstallationDirectory() ?: return null
    val entries = mutableListOf<Path>()

    try {
      val decompressor = Decompressor.Tar(downloadResult.downloadPath)
      decompressor.postProcessor(entries::add).extract(installationPath)
    } catch (e: Exception) {
      deleteInBackground(installationPath)
      throw e
    }

    val installFolder = installationPath.resolve(entries.first().fileName) // todo add desktop entry

    return InstallationResult(installFolder, downloadResult.suggestedIde)
  }

  override fun startUltimate(installationResult: InstallationResult): Boolean {
    val installed = installationResult.appPath
    val shellName = if (installationResult.suggestedIde.isPycharmProfessional()) "pycharm" else "idea"
    val shellPath = installed.resolve("bin").resolve("$shellName.sh")

    val command = GeneralCommandLine("sh", "-c", "${shellPath.pathString} ${project.basePath} $trialParameter &")
    
    return runCommand(command)
  }

  override fun getUltimateInstallationDirectory(): Path? {
    val localAppPath = System.getenv("XDG_DATA_HOME")?.toNioPathOrNull() 
                       ?: SystemProperties.getUserHome().toNioPathOrNull()?.resolve(".local/share")

    return localAppPath?.resolve("JetBrains")
  }
}