// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.eelJava

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstallRequestInfo
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItem
import com.intellij.openapi.projectRoots.impl.jdkDownloader.ReadJdkItemsForWSL
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.testFramework.eelJava.EelTestUtil.getEelFixtureEngineJavaHome
import com.intellij.platform.testFramework.eelJava.EelTestUtil.getFileSystemMount
import com.intellij.platform.testFramework.eelJava.EelTestUtil.getTeamcityWslJdkDefinition
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.exists

@ApiStatus.Internal
object EelTestJdkProvider {

  private val LOG = logger<EelTestJdkProvider>()

  @JvmStatic
  fun getJdkPath(eelDescriptor: EelDescriptor): @MultiRoutingFileSystemPath Path? {
    val installJdkOnWsl = when (EelTestUtil.getFixtureEngine()) {
      EelTestUtil.EelFixtureEngine.NONE -> {
        require(eelDescriptor is LocalEelDescriptor)
        return null
      }
      EelTestUtil.EelFixtureEngine.DOCKER -> {
        false
      }
      EelTestUtil.EelFixtureEngine.WSL -> {
        true
      }
    }

    val jdkPath = eelDescriptor.getPath(getEelFixtureEngineJavaHome().removePrefix(getFileSystemMount())).asNioPath()
    if (installJdkOnWsl) {
      if (jdkPath.resolve("bin/java").exists()) {
        LOG.info("JDK is installed in $jdkPath. Nothing to do.")
      }
      else {
        val definition = getTeamcityWslJdkDefinition()
        if (definition != null) {
          val jdkToInstall = readJdkItem(definition)
          ProgressManager.getInstance().runUnderEmptyProgress { progress ->
            val installer = JdkInstaller.getInstance()
            installer.installJdk(JdkInstallRequestInfo(jdkToInstall, jdkPath), progress, null)
          }
        }
      }
    }

    return jdkPath
  }

  private fun readJdkItem(path: Path): JdkItem {
    return ReadJdkItemsForWSL.readJdkItems(path)[0]
  }

  private fun ProgressManager.runUnderEmptyProgress(fn: (indicator: ProgressIndicator) -> Unit) {
    val indicator = EmptyProgressIndicator()
    runProcess({ fn(indicator) }, indicator)
  }
}