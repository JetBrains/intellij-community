// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.compiler

import com.intellij.openapi.application.edtWriteAction
import com.intellij.testFramework.useProjectAsync
import com.intellij.testFramework.utils.vfs.createFile
import com.intellij.testFramework.withProjectAsync
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.io.path.readText


class GradleRelativeConfigCalculatingTest : GradleRelativeConfigCalculatingTestCase() {

  @Test
  fun testGradleRelativeConfigEquality() {
    runBlocking {
      val projectInfo1 = projectInfo("project1/project") {
        withSettingsFile { setProjectName("project") }
        withBuildFile { withJavaPlugin() }
      }
      val projectInfo2 = projectInfo("project2/project") {
        withSettingsFile { setProjectName("project") }
        withBuildFile { withJavaPlugin() }
      }
      initProject(projectInfo1)
      initProject(projectInfo2)

      edtWriteAction {
        testRoot.createFile("project1/project/src/main/resources/dir/file-main.properties")
        testRoot.createFile("project1/project/src/test/resources/dir/file-test.properties")
        testRoot.createFile("project2/project/src/main/resources/dir/file-main.properties")
        testRoot.createFile("project2/project/src/test/resources/dir/file-test.properties")
      }

      val configFiles1 = openProject("project1/project")
        .withProjectAsync { project ->
          compileProject(project)

          assertFileExists("project1/project/out/production/resources/dir/file-main.properties")
          assertFileExists("project1/project/out/test/resources/dir/file-test.properties")
        }.useProjectAsync { project ->
          project.getGradleJpsResourceConfigs()
        }

      val configFiles2 = openProject("project2/project")
        .withProjectAsync { project ->
          compileProject(project)

          assertFileExists("project2/project/out/production/resources/dir/file-main.properties")
          assertFileExists("project2/project/out/test/resources/dir/file-test.properties")
        }.useProjectAsync { project ->
          project.getGradleJpsResourceConfigs()
        }

      Assertions.assertEquals(configFiles1.size, configFiles2.size) {
        """
          |Config files aren't equal
          | Config1: $configFiles1
          | Config2: $configFiles2
        """.trimMargin()
      }
      for ((configFile1, configFile2) in configFiles1.zip(configFiles2)) {
        val configFile1String = configFile1.readText()
        val configFile2String = configFile2.readText()
        Assertions.assertEquals(configFile1String, configFile2String) {
          """
            |Config contents aren't equal
            | Config1: $configFile1String
            | Config2: $configFile2String
          """.trimMargin()
        }
      }
    }
  }
}
