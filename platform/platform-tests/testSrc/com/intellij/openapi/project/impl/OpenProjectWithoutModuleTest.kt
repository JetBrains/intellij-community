// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.testFramework.useProject
import com.intellij.util.io.createDirectories
import com.intellij.workspaceModel.ide.ProjectRootEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.writeText

private const val REGISTRY_KEY = "ide.project.open.without.module"

class OpenProjectWithoutModuleTest : BareTestFixtureTestCase() {
  @Rule @JvmField val tempDir = TempDirectory()

  @After fun cleanup() {
    val projects = ProjectUtilCore.getOpenProjects()
    if (projects.isNotEmpty()) {
      val message = "Leaked projects: ${projects.toList()}"
      projects.forEach(PlatformTestUtil::forceCloseProjectWithoutSaving)
      Assert.fail(message)
    }
  }

  @Test fun openWithoutModuleKeepsZeroModulesAndRegistersProjectRoot() {
    Registry.get(REGISTRY_KEY).setValue(true, testRootDisposable)
    val projectDir = tempDir.root.toPath().resolve("without-module").createDirectories()
    openProject(projectDir) { project ->
      assertThat(ModuleManager.getInstance(project).modules).isEmpty()
      val workspaceModel = WorkspaceModel.getInstance(project)
      val expectedRoot = projectDir.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager())
      val roots = workspaceModel.currentSnapshot.entities(ProjectRootEntity::class.java).map { it.root }.toList()
      assertThat(roots).containsExactly(expectedRoot)
    }
  }

  @Test fun reopenWithoutModuleKeepsZeroModules() {
    Registry.get(REGISTRY_KEY).setValue(true, testRootDisposable)
    val projectDir = tempDir.root.toPath().resolve("reopen-without-module").createDirectories()
    openProject(projectDir) { project ->
      assertThat(ModuleManager.getInstance(project).modules).isEmpty()
    }

    val reopened = runBlocking {
      ProjectManagerEx.getInstanceEx().openProjectAsync(projectDir, OpenProjectTask {
        forceOpenInNewFrame = true
        runConversionBeforeOpen = false
        showWelcomeScreen = false
        runConfigurators = true
        projectRootDir = projectDir
        beforeOpenTasks = listOf { project ->
          project.putUserData(PROJECT_LOADED_FROM_CACHE_BUT_HAS_NO_MODULES, true)
          true
        }
      })
    }
    assertThat(reopened).isNotNull()
    reopened!!.useProject { project ->
      assertThat(ModuleManager.getInstance(project).modules).isEmpty()
    }
  }

  @Test fun openWithDefaultRegistryValueCreatesModule() {
    val projectDir = tempDir.root.toPath().resolve("with-module").createDirectories()
    openProject(projectDir) { project ->
      assertThat(ModuleManager.getInstance(project).modules).hasSize(1)
    }
  }

  @Test fun openWithoutModuleIgnoresModuleFilesInStore() {
    Registry.get(REGISTRY_KEY).setValue(true, testRootDisposable)
    val projectDir = tempDir.root.toPath().resolve("legacy-store").createDirectories()
    writeLegacyModuleFiles(projectDir)
    openProject(projectDir, isNewProject = false) { project ->
      assertThat(ModuleManager.getInstance(project).modules).isEmpty()
    }
  }

  @Test fun openWithDefaultRegistryValueLoadsModuleFromStore() {
    val projectDir = tempDir.root.toPath().resolve("legacy-store-default").createDirectories()
    writeLegacyModuleFiles(projectDir)
    openProject(projectDir, isNewProject = false) { project ->
      val modules = ModuleManager.getInstance(project).modules
      assertThat(modules).hasSize(1)
      assertThat(modules.single().name).isEqualTo("legacy")
    }
  }

  private fun writeLegacyModuleFiles(projectDir: Path) {
    val ideaDir = projectDir.resolve(".idea").createDirectories()
    ideaDir.resolve("modules.xml").writeText($$"""
      <?xml version="1.0" encoding="UTF-8"?>
      <project version="4">
        <component name="ProjectModuleManager">
          <modules>
            <module fileurl="file://$PROJECT_DIR$/.idea/legacy.iml" filepath="$PROJECT_DIR$/.idea/legacy.iml" />
          </modules>
        </component>
      </project>""".trimIndent())
    ideaDir.resolve("legacy.iml").writeText($$"""
      <?xml version="1.0" encoding="UTF-8"?>
      <module type="EMPTY_MODULE" version="4">
        <component name="NewModuleRootManager">
          <content url="file://$MODULE_DIR$/.." />
        </component>
      </module>""".trimIndent())
  }

  private fun openProject(projectDir: Path, isNewProject: Boolean = true, checks: (Project) -> Unit) {
    val project = runBlocking {
      ProjectManagerEx.getInstanceEx().openProjectAsync(projectDir, OpenProjectTask {
        forceOpenInNewFrame = true
        runConversionBeforeOpen = false
        showWelcomeScreen = false
        this.isNewProject = isNewProject
        useDefaultProjectAsTemplate = false
        runConfigurators = true
        projectRootDir = projectDir
      })
    }
    assertThat(project).isNotNull()
    project!!.useProject(action = checks)
  }
}
