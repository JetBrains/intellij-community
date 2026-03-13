// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.TestObservation
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.eclipse.importWizard.EclipseProjectOpenProcessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
class EclipseProjectOpenProcessorTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @Test
  fun testQuickImportOpensProjectWithSingleModule() = runBlocking {
    val workspace = tempDirectory.newDirectoryPath("workspace")
    createEclipseProject(workspace, "single")

    assertQuickImportOpensProject(workspace, "single", listOf("single"))
  }

  @Test
  fun testQuickImportOpensProjectWithManyModules() = runBlocking {
    val workspace = tempDirectory.newDirectoryPath("workspace")
    val moduleA = workspace.resolve("moduleA")
    val moduleB = workspace.resolve("moduleB")
    val moduleC = moduleB.resolve("moduleC")

    createEclipseProject(workspace, "workspace")
    createEclipseProject(moduleA, "moduleA")
    createEclipseProject(moduleB, "moduleB")
    createEclipseProject(moduleC, "moduleC")

    val expectedModuleNames = listOf("workspace", "moduleA", "moduleB", "moduleC")
    assertQuickImportOpensProject(workspace, "workspace", expectedModuleNames)
  }

  private suspend fun assertQuickImportOpensProject(workspace: Path, expectedProjectName: String, expectedModuleNames: List<String>) {
    val processor = EclipseProjectOpenProcessor()
    val wizardDisposable = Disposer.newDisposable()
    val context = WizardContext(null, wizardDisposable)

    try {
      val projectFile = workspace.resolve(EclipseXml.PROJECT_FILE)
      val virtualProjectFile =
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectFile) ?: error("Cannot find $projectFile in VFS")

      val quickImported = runInEdtAndGet {
        processor.doQuickImport(virtualProjectFile, context)
      }
      assertTrue(quickImported)
      assertEquals(expectedProjectName, context.projectName)

      closeOpenedProjectsIfFailAsync {
        processor.openProjectAsync(virtualProjectFile, null, true) ?: error("Failed to open project from $projectFile")
      }.useProjectAsync { project ->
        TestObservation.awaitConfiguration(project)
        assertEquals(expectedProjectName, project.name)
        val moduleNames = ModuleManager.getInstance(project).modules.map { it.name }.sorted()
        assertEquals(expectedModuleNames.sorted(), moduleNames.sorted())
      }
    }
    finally {
      Disposer.dispose(wizardDisposable)
    }
  }

  private fun createEclipseProject(projectDir: Path, projectName: String) {
    Files.createDirectories(projectDir)
    Files.writeString(projectDir.resolve(EclipseXml.PROJECT_FILE), """
                        <projectDescription>
                          <name>$projectName</name>
                        </projectDescription>
                      """.trimIndent())
  }
}