// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import java.io.File

class GradleToolWindowOldGroupingTest : GradleToolWindowTest() {
  override fun setUp() {
    super.setUp()
    currentExternalProjectSettings.isUseQualifiedModuleNames = false
  }

  override val path: String
    get() {
    val testDataPath = super.path
    val testDataForOldGrouping = "$testDataPath.old"
    if (File(testDataForOldGrouping).exists()) {
      return testDataForOldGrouping
    }
    else {
      return testDataPath
    }
  }
}
