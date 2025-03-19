// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.module

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.closeOpenedProjectsIfFailAsync
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.withProjectAsync
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.name

@TestApplication
abstract class ModuleAssertionTestCase {

  @TempDir
  lateinit var testDirectory: Path

  suspend fun generateProject(projectRoot: Path, configure: ProjectConfigurator.() -> Unit): Project {
    val projectManager = ProjectManagerEx.getInstanceEx()
    val project = closeOpenedProjectsIfFailAsync {
      projectManager.newProjectAsync(projectRoot, OpenProjectTask {
        isNewProject = true
        runConfigurators = true
        projectName = projectRoot.name
      })
    }
    project.withProjectAsync {
      val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
      project.workspaceModel.update("test project initialisation") { storage ->
        ProjectConfigurator(storage, virtualFileUrlManager, projectRoot)
          .configure()
      }
    }
    return project
  }

  class ProjectConfigurator(
    private val storage: MutableEntityStorage,
    private val virtualFileUrlManager: VirtualFileUrlManager,
    private val projectRoot: Path
  ) {

    fun addModuleEntity(moduleName: String, relativePath: String, configure: ContentRootConfigurator.() -> Unit = {}) {
      addModuleEntity(moduleName) {
        addContentRoot(relativePath, configure)
      }
    }

    fun addModuleEntity(moduleName: String, configure: ModuleConfigurator.() -> Unit = {}) {
      val moduleEntity = ModuleEntity(moduleName, emptyList(), NonPersistentEntitySource)
      storage addEntity moduleEntity

      ModuleConfigurator(storage, virtualFileUrlManager, projectRoot, moduleEntity)
        .configure()
    }
  }

  class ModuleConfigurator(
    private val storage: MutableEntityStorage,
    private val virtualFileUrlManager: VirtualFileUrlManager,
    private val projectRoot: Path,
    private val moduleEntity: ModuleEntity.Builder,
  ) {

    fun addContentRoot(relativePath: String, configure: ContentRootConfigurator.() -> Unit = {}) {
      val contentRootPath = projectRoot.resolve(relativePath).normalize()
      val contentRoot = contentRootPath.toVirtualFileUrl(virtualFileUrlManager)

      val contentRootEntity = ContentRootEntity(contentRoot, emptyList(), NonPersistentEntitySource) {
        module = moduleEntity
      }
      storage addEntity contentRootEntity

      ContentRootConfigurator(storage, virtualFileUrlManager, projectRoot, contentRootEntity)
        .configure()
    }
  }

  class ContentRootConfigurator(
    private val storage: MutableEntityStorage,
    private val virtualFileUrlManager: VirtualFileUrlManager,
    private val projectRoot: Path,
    private val contentRootEntity: ContentRootEntity.Builder,
  ) {

    fun addSourceRoot(typeId: SourceRootTypeId, relativePath: String) {
      val sourceRootPath = projectRoot.resolve(relativePath).normalize()
      val sourceRoot = sourceRootPath.toVirtualFileUrl(virtualFileUrlManager)

      val sourceRootEntity = SourceRootEntity(sourceRoot, typeId, NonPersistentEntitySource) {
        contentRoot = contentRootEntity
      }
      storage addEntity sourceRootEntity
    }
  }
}