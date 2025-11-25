// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.ide.impl.ProjectUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.createTestOpenProjectOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import kotlin.io.path.createDirectories

class ProjectCannotBeOpenedTwiceTest {
  companion object {
    @ClassRule
    @JvmField
    val appRule: ApplicationRule = ApplicationRule()
  }

  @Test
  fun `test the same project cannot be opened twice`() {
    runBlocking {
      val name = "project that is opened twice"
      val projectFile = TemporaryDirectory.generateTemporaryPath(name)
      projectFile.createDirectories()
      val options = createTestOpenProjectOptions().copy(projectName = name, projectRootDir = projectFile)
      val project1 = async {
        ProjectUtil.openOrImportAsync(projectFile, options)
      }
      val project2 = async {
        ProjectUtil.openOrImportAsync(projectFile, options)
      }
      project1.join()
      project2.join()

      val openProjects = ProjectManager.getInstance().openProjects.filter { it.name == name }
      assertThat(openProjects).hasSize(1)

      openProjects.forEach {
        it.closeProjectAsync()
      }
    }
  }
}