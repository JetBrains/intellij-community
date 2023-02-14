// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.compiler

import com.intellij.openapi.application.writeAction
import com.intellij.testFramework.useProjectAsync
import com.intellij.testFramework.utils.vfs.createFile
import com.intellij.testFramework.withProjectAsync
import com.intellij.util.io.readBytes
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Path


class GradleRelativeConfigCalculatingTest : GradleRelativeConfigCalculatingTestCase() {

  @Test
  fun testGradleRelativeConfigEquality() {
    runBlocking {
      val projectInfo1 = projectInfo("project1") {
        withSettingsFile { setProjectName("project1") }
        withBuildFile { withJavaPlugin() }
      }
      val projectInfo2 = projectInfo("project2") {
        withSettingsFile { setProjectName("project2") }
        withBuildFile { withJavaPlugin() }
      }
      initProject(projectInfo1)
      initProject(projectInfo2)

      writeAction {
        testRoot.createFile("project1/src/main/resources/dir/file-main.properties")
        testRoot.createFile("project1/src/test/resources/dir/file-test.properties")
        testRoot.createFile("project2/src/main/resources/dir/file-main.properties")
        testRoot.createFile("project2/src/test/resources/dir/file-test.properties")
      }

      val configFiles1 = openProject("project1")
        .withProjectAsync { project ->
          compileProject(project)

          assertFileExists("project1/out/production/resources/dir/file-main.properties")
          assertFileExists("project1/out/test/resources/dir/file-test.properties")
        }.useProjectAsync { project ->
          project.getGradleJpsResourceConfigs()

          // Todo(Aleksei Cherepanov): remove it
          emptyList<Path>()
        }

      val configFiles2 = openProject("project2")
        .withProjectAsync { project ->
          compileProject(project)

          assertFileExists("project2/out/production/resources/dir/file-main.properties")
          assertFileExists("project2/out/test/resources/dir/file-test.properties")
        }.useProjectAsync { project ->
          project.getGradleJpsResourceConfigs()

          // Todo(Aleksei Cherepanov): remove it
          emptyList<Path>()
        }

      Assertions.assertEquals(configFiles1.size, configFiles2.size) {
        """
          |Config files aren't equal
          | Config1: $configFiles1
          | Config2: $configFiles2
        """.trimMargin()
      }
      for ((configFile1, configFile2) in configFiles1.zip(configFiles2)) {
        Assertions.assertEquals(configFile1.readBytes(), configFile2.readBytes()) {
          """
            |Config contents aren't equal
            | Config1: $configFile1
            | Config2: $configFile2
          """.trimMargin()
        }
      }
    }
  }
}