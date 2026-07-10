// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.storage.entities
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class JpsProjectModelSynchronizerTest {
  @Test
  fun `module library is loaded when project is opened with all module files present`(
    @TempDir projectDir: Path,
  ): Unit = timeoutRunBlocking(timeout = 30.seconds) {
    projectDir.resolve(".idea").createDirectories()
    projectDir.resolve("main.iml").writeText(moduleIml(projectDir))
    val moduleDir = projectDir.resolve("module")
    moduleDir.createDirectories()
    moduleDir.resolve("module.iml").writeText(moduleIml(moduleDir, withLibrary = true))
    projectDir.resolve(".idea/modules.xml").writeText(modulesXml("main.iml", "module/module.iml"))

    ProjectUtil.openProject(projectDir, OpenProjectTask { createModule = false })!!.useProjectAsync { project ->
      refreshProject(projectDir)
      (project.workspaceModel as WorkspaceModelInternal).awaitSynchronizationWithJpsModel()

      val libraryEntities = project.workspaceModel.currentSnapshot.entities<LibraryEntity>().toList()
      assertThat(libraryEntities.any { it.name == "junit.jupiter" }).isTrue
    }
  }

  @Test
  fun `module library is loaded when module and modules file are updated simultaneously`(
    @TempDir projectDir: Path,
  ): Unit = timeoutRunBlocking(timeout = 30.seconds) {
    projectDir.resolve(".idea").createDirectories()
    projectDir.resolve("main.iml").writeText(moduleIml(projectDir))
    val modulesFile = projectDir.resolve(".idea/modules.xml")
    modulesFile.writeText(modulesXml("main.iml"))

    ProjectUtil.openProject(projectDir, OpenProjectTask { createModule = false })!!.useProjectAsync { project ->
      refreshProject(projectDir)
      (project.workspaceModel as WorkspaceModelInternal).awaitSynchronizationWithJpsModel()

      val moduleDir = projectDir.resolve("module")
      moduleDir.createDirectories()
      moduleDir.resolve("module.iml").writeText(moduleIml(moduleDir, withLibrary = true))
      modulesFile.writeText(modulesXml("main.iml", "module/module.iml"))
      refreshProject(projectDir)
      (project.workspaceModel as WorkspaceModelInternal).awaitSynchronizationWithJpsModel()

      val libraryEntities = project.workspaceModel.currentSnapshot.entities<LibraryEntity>().toList()
      assertThat(libraryEntities.any { it.name == "junit.jupiter" }).isTrue
    }
  }

  @Test
  fun `module library is loaded when module directory is created after project synchronization`(
    @TempDir projectDir: Path,
  ): Unit = timeoutRunBlocking(timeout = 30.seconds) {
    projectDir.resolve(".idea").createDirectories()
    projectDir.resolve("main.iml").writeText(moduleIml(projectDir))
    val modulesFile = projectDir.resolve(".idea/modules.xml")
    modulesFile.writeText(modulesXml("main.iml"))

    ProjectUtil.openProject(projectDir, OpenProjectTask { createModule = false })!!.useProjectAsync { project ->
      refreshProject(projectDir)
      (project.workspaceModel as WorkspaceModelInternal).awaitSynchronizationWithJpsModel()

      modulesFile.writeText(modulesXml("main.iml", "module/module.iml"))
      refreshProject(projectDir)
      (project.workspaceModel as WorkspaceModelInternal).awaitSynchronizationWithJpsModel()

      val moduleDir = projectDir.resolve("module")
      moduleDir.createDirectories()
      moduleDir.resolve("module.iml").writeText(moduleIml(moduleDir, withLibrary = true))
      refreshProject(projectDir)
      (project.workspaceModel as WorkspaceModelInternal).awaitSynchronizationWithJpsModel()

      val libraryEntities = project.workspaceModel.currentSnapshot.entities<LibraryEntity>().toList()
      val libraryNames = libraryEntities.map { it.name }
      assertThat(libraryNames).contains("junit.jupiter")
    }
  }

  private companion object {

    private suspend fun refreshProject(projectDir: Path) {
      val paths = Files.walk(projectDir).use { it.toList().toTypedArray() }
      VfsUtil.markDirtyAndRefresh(false, false, false, *paths)
      delay(1.seconds)
    }

    private fun moduleIml(contentRoot: Path, withLibrary: Boolean = false): String {
      val moduleLibrary = if (withLibrary) """
        <orderEntry type="module-library">
          <library name="junit.jupiter">
            <CLASSES />
            <JAVADOC />
            <SOURCES />
          </library>
        </orderEntry>
      """.trimIndent() else ""
      return """<?xml version="1.0" encoding="UTF-8"?>
        <module type="JAVA_MODULE" version="4">
          <component name="NewModuleRootManager">
            <content url="${contentRoot.toUri()}" />
            $moduleLibrary
            <orderEntry type="sourceFolder" forTests="false" />
          </component>
        </module>
      """.trimIndent()
    }

    private fun modulesXml(vararg modulePaths: String): String {
      val modules = modulePaths.joinToString("\n") { path ->
        val modulePath = buildString {
          append('$')
          append("PROJECT_DIR")
          append('$')
          append('/')
          append(path)
        }
        "      <module fileurl=\"file://$modulePath\" filepath=\"$modulePath\" />"
      }
      return """<?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="ProjectModuleManager">
            <modules>
        $modules
            </modules>
          </component>
        </project>
      """.trimIndent()
    }
  }
}
