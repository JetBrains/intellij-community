// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import java.io.File

class GradleToolWindowOldGroupingTest : GradleToolWindowTest() {
  override fun setUp() {
    super.setUp()
    currentExternalProjectSettings.setUseQualifiedModuleNames(false)
  }

  override fun getPath(): String {
    val testDataPath = super.getPath()
    val testDataForOldGrouping = "$testDataPath.old"
    if (File(testDataForOldGrouping).exists()) {
      return testDataForOldGrouping
    }
    else {
      return testDataPath
    }
  }
}
