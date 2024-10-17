// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.project.guessProjectDir
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmHelper
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

class GradleDaemonToolchainCriteriaTest : GradleImportingTestCase() {

  @Test
  @TargetVersions("8.8+")
  fun testUpdatingDaemonJvmCriteria() {
    importProject("")
    val projectDir = project.guessProjectDir()!!
    GradleDaemonJvmHelper.updateProjectDaemonJvmCriteria(project, projectDir.path, "17", "JETBRAINS", ProgressExecutionMode.NO_PROGRESS_SYNC)

    GradleDaemonJvmPropertiesFile.getProperties(projectDir.toNioPath())!!.run {
      assertEquals("17", version?.value)
      assertEquals("JETBRAINS", vendor?.value)
    }
  }
}