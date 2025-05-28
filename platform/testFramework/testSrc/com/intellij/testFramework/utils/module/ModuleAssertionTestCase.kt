// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.module

import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.DependencyScope.COMPILE
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import java.nio.file.Path

@TestApplication
abstract class ModuleAssertionTestCase {

  val project by projectFixture()
  val projectRoot: Path get() = Path.of(project.basePath!!)

  suspend fun WorkspaceModel.update(updater: MutableEntityStorage.() -> Unit) =
    update("Test description", updater)

  fun MutableEntityStorage.addModuleEntity(moduleName: String, relativePath: String, configure: ContentRootEntity.Builder.() -> Unit = {}) {
    addModuleEntity(moduleName) {
      addContentRoot(relativePath, configure)
    }
  }

  fun MutableEntityStorage.addModuleEntity(moduleName: String, configure: ModuleEntity.Builder.() -> Unit = {}) {
    addEntity(ModuleEntity(moduleName, emptyList(), NonPersistentEntitySource) {
      configure()
    })
  }

  fun ModuleEntity.Builder.addContentRoot(relativePath: String, configure: ContentRootEntity.Builder.() -> Unit = {}) {
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    val contentRootPath = projectRoot.resolve(relativePath).normalize()
    val contentRoot = contentRootPath.toVirtualFileUrl(virtualFileUrlManager)
    contentRoots += ContentRootEntity(contentRoot, emptyList(), NonPersistentEntitySource) {
      configure()
    }
  }

  fun ContentRootEntity.Builder.addSourceRoot(typeId: SourceRootTypeId, relativePath: String) {
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    val sourceRootPath = projectRoot.resolve(relativePath).normalize()
    val sourceRoot = sourceRootPath.toVirtualFileUrl(virtualFileUrlManager)
    sourceRoots += SourceRootEntity(sourceRoot, typeId, NonPersistentEntitySource)
  }

  fun MutableEntityStorage.addLibraryEntity(libraryName: String) {
    addEntity(LibraryEntity(libraryName, LibraryTableId.ProjectLibraryTableId, emptyList(), NonPersistentEntitySource))
  }

  fun ModuleEntity.Builder.addLibraryDependency(libraryName: String) {
    dependencies += LibraryDependency(LibraryId(libraryName, LibraryTableId.ProjectLibraryTableId), exported = false, COMPILE)
  }

  fun ModuleEntity.Builder.addModuleDependency(moduleName: String) {
    dependencies += ModuleDependency(ModuleId(moduleName), exported = false, COMPILE, productionOnTest = false)
  }
}