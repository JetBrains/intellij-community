// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.toOpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.TemporaryDirectoryExtension
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertTrue

@TestApplication
internal class ProjectOpenProcessorOptionsTest {
  @JvmField
  @RegisterExtension
  val tempDir = TemporaryDirectoryExtension()

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `project open processor can pass original OpenProjectTask to ProjectManagerEx`() {
    runBlocking(Dispatchers.Default) {
      ExtensionTestUtil.maskExtensions(
        ProjectOpenProcessor.EXTENSION_POINT_NAME,
        listOf(TestProjectOpenProcessor()),
        disposable,
        fireEvents = false,
      )

      val projectDir = tempDir.newPath("imported-project")
      projectDir.createDirectories()
      projectDir.resolve(TEST_PROJECT_MARKER).writeText("")

      var beforeInitInvoked = false
      val project = ProjectUtil.openOrImportAsync(projectDir, OpenProjectTask {
        runConversionBeforeOpen = false
        runConfigurators = false
        showWelcomeScreen = false
        projectName = "imported-project"
        beforeInit = { beforeInitInvoked = true }
      })

      project?.closeProjectAsync()
      assertThat(project).isNotNull()
      assertTrue(beforeInitInvoked)
    }
  }

  private class TestProjectOpenProcessor : ProjectOpenProcessor() {
    override val name: String = "Test"

    override fun canOpenProject(file: VirtualFile): Boolean {
      return file.isDirectory && file.findChild(TEST_PROJECT_MARKER) != null
    }

    override suspend fun openProjectAsync(
      virtualFile: VirtualFile,
      projectOpenOptions: ProjectOpenProcessor.ProjectOpenOptions,
    ): Project? {
      val projectDir = virtualFile.toNioPath()
      val options = projectOpenOptions.toOpenProjectTask().copy(
        projectRootDir = projectDir,
      )
      return ProjectManagerEx.getInstanceEx().openProjectAsync(projectDir, options)
    }
  }
}

private const val TEST_PROJECT_MARKER = "test.project.marker"
