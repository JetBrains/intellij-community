// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil.ensureExists
import com.intellij.openapi.util.io.FileUtil.toSystemDependentName
import com.intellij.testGuiFramework.launcher.GuiTestOptions
import org.fest.assertions.Assertions.assertThat
import java.io.File

object GuiTestPaths {

  val failedTestScreenshotDir: File by lazy {
    val dirPath = File(guiTestRootDirPath, "failures")
    ensureExists(dirPath)
    return@lazy dirPath
  }

  val failedTestVideoDirPath: File by lazy {
    val dirPath = File(failedTestScreenshotDir, "video")
    ensureExists(dirPath)
    return@lazy dirPath
  }

  val testScreenshotDirPath: File by lazy {
    val dirPath = File(guiTestRootDirPath, "screenshots")
    ensureExists(dirPath)
    return@lazy dirPath
  }

  private val guiTestRootDirPath: File by lazy {
    if (!GuiTestOptions.guiTestRootDirPath.isNullOrEmpty()) {
      val rootDirPath = File(GuiTestOptions.guiTestRootDirPath)
      if (rootDirPath.isDirectory) {
        return@lazy rootDirPath
      }
    }
    val systemDir = toSystemDependentName(PathManager.getSystemPath())
    assertThat(systemDir).isNotEmpty
    val logDir = File(systemDir, "log")
    val guiTestsDir = File(logDir, "gui-tests")
    ensureExists(guiTestsDir)
    return@lazy guiTestsDir
  }

}
