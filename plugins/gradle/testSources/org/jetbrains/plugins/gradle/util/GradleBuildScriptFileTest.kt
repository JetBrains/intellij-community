// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import junit.framework.TestCase
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.junit.Test
import java.io.File

class GradleBuildScriptFileTest : GradleImportingTestCase() {

  @Test
  fun `test gradle build script source file`() {
    createSettingsFile("include 'project1', 'project2'")
    createProjectSubFile("project1/build.gradle")
    createProjectSubFile("project2/build.gradle.kts")
    importProject("")
    assertModules("project", "project.project1", "project.project2")

    assertModuleBuildScript("project", "build.gradle")
    assertModuleBuildScript("project.project1", "project1/build.gradle")
    assertModuleBuildScript("project.project2", "project2/build.gradle.kts")
  }

  private fun assertModuleBuildScript(moduleName: String, expectedRelativePath: String?) {
    val actualScriptFile = GradleUtil.getGradleBuildScriptSource(getModule(moduleName))
    if (expectedRelativePath == null) {
      TestCase.assertNull(actualScriptFile)
    }
    else {
      val root = File(projectPath)
      val expected = File(root, expectedRelativePath).canonicalPath
      val actual = actualScriptFile?.toNioPath()?.toFile()?.canonicalPath
      TestCase.assertEquals(expected, actual)
    }
  }
}