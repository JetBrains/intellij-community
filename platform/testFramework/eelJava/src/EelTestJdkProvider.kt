// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.eelJava

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstallRequestInfo
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItem
import com.intellij.openapi.projectRoots.impl.jdkDownloader.ReadJdkItemsForWSL
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.exists

@ApiStatus.Internal
object EelTestJdkProvider {

  private val LOG = logger<EelTestJdkProvider>()

  @JvmStatic
  fun getJdkPath(): Path? {
    val engine = getFixtureEngine()
    if (engine == EelFixtureEngine.NONE) {
      return null
    }
    val jdkPath = getEelFixtureEngineJavaHome()
    if (engine == EelFixtureEngine.WSL) {
      val definition = getTeamcityWslJdkDefinition()
      if (definition != null) {
        val jdkToInstall = readJdkItem(definition)
        checkOrInstallJDK(jdkPath, jdkToInstall)
      }
    }
    return jdkPath
  }

  private fun readJdkItem(path: Path): JdkItem {
    return ReadJdkItemsForWSL.readJdkItems(path)[0]
  }

  private fun checkOrInstallJDK(path: Path, jdkItem: JdkItem) {
    if (path.resolve("bin/java").exists()) {
      LOG.info("JDK is installed in $path. Nothing to do.")
    }
    else {
      ProgressManager.getInstance().runUnderEmptyProgress { progress ->
        val installer = JdkInstaller.getInstance()
        installer.installJdk(JdkInstallRequestInfo(jdkItem, path), progress, null)
      }
    }
  }

  private fun ProgressManager.runUnderEmptyProgress(fn: (indicator: ProgressIndicator) -> Unit) {
    val indicator = EmptyProgressIndicator()
    runProcess({ fn(indicator) }, indicator)
  }
}