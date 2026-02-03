// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.moduleAssertion.ContentRootAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.ExModuleOptionAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExternalSystemModuleOptionsEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.impl.extensions.GradleBaseSyncExtension
import org.jetbrains.plugins.gradle.service.syncAction.impl.extensions.GradleJpsSyncExtension
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.entity.GradleTestEntitySource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.file.Path

@TestApplication
class GradleJpsSyncExtensionTest {

  private val project by projectFixture()
  private val projectPath get() = Path.of(project.basePath!!)

  private val virtualFileUrlManger get() = project.workspaceModel.getVirtualFileUrlManager()

  private fun virtualFileUrl(path: Path): VirtualFileUrl {
    return path.toVirtualFileUrl(virtualFileUrlManger)
  }

  @Test
  fun `test GradleJpsSyncExtension#removeModulesWithUsedContentRoots`(): Unit = runBlocking {
    val moduleNames = (0 until 10).map { "module$it" }

    val phase = GradleSyncPhase.PROJECT_MODEL_PHASE // Any
    val entitySource = GradleTestEntitySource(projectPath.toCanonicalPath(), phase)

    val projectStorage = MutableEntityStorage.create()
    for (moduleName in moduleNames) {
      val modulePath = projectPath.resolve(moduleName)
      projectStorage addEntity ModuleEntity(moduleName, emptyList(), NonPersistentEntitySource) {
        contentRoots += ContentRootEntity(virtualFileUrl(modulePath), emptyList(), NonPersistentEntitySource)
        exModuleOptions = null
      }
    }

    val syncStorage = MutableEntityStorage.create()
    for (moduleName in moduleNames) {
      val modulePath = projectPath.resolve(moduleName)
      syncStorage addEntity ModuleEntity(moduleName, emptyList(), entitySource) {
        contentRoots += ContentRootEntity(virtualFileUrl(modulePath), emptyList(), entitySource)
        exModuleOptions = ExternalSystemModuleOptionsEntity(entitySource) {
          externalSystem = GradleConstants.SYSTEM_ID.id
          rootProjectPath = projectPath.toCanonicalPath()
          linkedProjectPath = projectPath.resolve(moduleName).toCanonicalPath()
          linkedProjectId = moduleName
        }
      }
    }

    val context = mock<ProjectResolverContext> {
      on { project } doReturn project
      on { projectPath } doReturn projectPath.toCanonicalPath()
    }
    GradleJpsSyncExtension().updateProjectModel(context, syncStorage, projectStorage, phase)
    GradleBaseSyncExtension().updateProjectModel(context, syncStorage, projectStorage, phase)
    Mockito.reset(context)

    ModuleAssertions.assertModules(projectStorage, moduleNames)
    for (moduleName in moduleNames) {
      val modulePath = projectPath.resolve(moduleName)
      ModuleAssertions.assertModuleEntity(projectStorage, moduleName) { module ->
        Assertions.assertEquals(entitySource, module.entitySource)
        ContentRootAssertions.assertContentRoots(virtualFileUrlManger, module, listOf(modulePath))
        ExModuleOptionAssertions.assertExModuleOptions(module) { exModuleOptions ->
          Assertions.assertEquals(entitySource, exModuleOptions.entitySource)
          Assertions.assertEquals(GradleConstants.SYSTEM_ID.id, exModuleOptions.externalSystem)
          Assertions.assertEquals(projectPath.toCanonicalPath(), exModuleOptions.rootProjectPath)
          Assertions.assertEquals(projectPath.resolve(moduleName).toCanonicalPath(), exModuleOptions.linkedProjectPath)
          Assertions.assertEquals(moduleName, exModuleOptions.linkedProjectId)
        }
      }
    }
  }

  @Test
  fun `test GradleJpsSyncExtension#renameDuplicatedModules`(): Unit = runBlocking {
    val moduleNames = (0 until 10).map { "module$it" }
    val renamedModuleNames = moduleNames.map { "project2.$it" }

    val phase = GradleSyncPhase.PROJECT_MODEL_PHASE // Any
    val projectPath1 = projectPath.resolve("project1")
    val projectPath2 = projectPath.resolve("project2")
    val entitySource1 = GradleTestEntitySource(projectPath1.toCanonicalPath(), phase)
    val entitySource2 = GradleTestEntitySource(projectPath2.toCanonicalPath(), phase)

    val projectStorage = MutableEntityStorage.create()
    for (moduleName in moduleNames) {
      projectStorage addEntity ModuleEntity(moduleName, emptyList(), entitySource1)
    }

    val syncStorage = MutableEntityStorage.create()
    for (moduleName in moduleNames) {
      syncStorage addEntity ModuleEntity(moduleName, emptyList(), entitySource2)
    }

    val context = mock<ProjectResolverContext> {
      on { project } doReturn project
      on { projectPath } doReturn projectPath2.toCanonicalPath()
    }
    GradleJpsSyncExtension().updateProjectModel(context, syncStorage, projectStorage, phase)
    GradleBaseSyncExtension().updateProjectModel(context, syncStorage, projectStorage, phase)
    Mockito.reset(context)

    ModuleAssertions.assertModules(projectStorage, moduleNames + renamedModuleNames)
    for (moduleName in moduleNames) {
      ModuleAssertions.assertModuleEntity(projectStorage, moduleName) { module ->
        Assertions.assertEquals(entitySource1, module.entitySource)
      }
    }
    for (moduleName in renamedModuleNames) {
      ModuleAssertions.assertModuleEntity(projectStorage, moduleName) { module ->
        Assertions.assertEquals(entitySource2, module.entitySource)
      }
    }
  }
}