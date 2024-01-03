// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.linux

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.DownloadResult
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.InstallationResult
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.UltimateInstaller
import com.intellij.openapi.updateSettings.impl.upgradeToUltimate.installation.runCommand
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.util.SystemProperties
import com.intellij.util.io.Decompressor
import com.intellij.util.system.CpuArch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.pathString

internal class LinuxInstaller(scope: CoroutineScope) : UltimateInstaller(scope) {
  override val postfix = if (CpuArch.isArm64()) "-aarch64.tar.gz" else ".tar.gz"
  
  @OptIn(ExperimentalPathApi::class)
  override fun install(downloadResult: DownloadResult): InstallationResult? {
    val installationPath = getUltimateInstallationDirectory() ?: return null
    val entries = mutableListOf<Path>()
    
    try {
      val decompressor = Decompressor.Tar(downloadResult.downloadPath)
      decompressor
        .postProcessor(entries::add)
        .extract(installationPath.resolve("Jetbrains"))
    } catch (e: Exception) {
      scope.launch { installationPath.deleteRecursively() }
      throw e
    }
    
    val installFolder = installationPath.resolve(entries.first().fileName)
    
    return InstallationResult(installFolder)
  }

  override fun startUltimate(installationResult: InstallationResult): Boolean {
    val installed = installationResult.appPath
    val shellPath = installed.resolve("bin").resolve("idea.sh")
    
    val command = GeneralCommandLine("/usr/bin/setsid")
      .withParameters(shellPath.pathString)
    
    return runCommand(command)
  }

  override fun getUltimateInstallationDirectory(): Path? {
     return System.getenv("XDG_DATA_HOME").toNioPathOrNull()
            ?: SystemProperties.getUserHome().toNioPathOrNull()?.resolve(".local/share")
  }
}