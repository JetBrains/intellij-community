// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion.moduleAssertion

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.junit.jupiter.api.Assertions
import java.nio.file.Path

object ContentRootAssertions {

  @JvmStatic
  fun assertContentRoots(module: Module, vararg expectedRoots: Path) {
    assertContentRoots(module, expectedRoots.asList())
  }

  @JvmStatic
  fun assertContentRoots(module: Module, expectedRoots: List<Path>) {
    assertContentRoots(module.project, module.name, expectedRoots)
  }

  @JvmStatic
  fun assertContentRoots(project: Project, moduleName: String, vararg expectedRoots: Path) {
    assertContentRoots(project, moduleName, expectedRoots.asList())
  }

  @JvmStatic
  fun assertContentRoots(project: Project, moduleName: String, expectedRoots: List<Path>) {
    val workspaceModel = project.workspaceModel
    val storage = workspaceModel.currentSnapshot
    val virtualFileUrlManager = workspaceModel.getVirtualFileUrlManager()
    assertContentRoots(virtualFileUrlManager, storage, moduleName, expectedRoots)
  }

  @JvmStatic
  fun assertContentRoots(virtualFileUrlManager: VirtualFileUrlManager, storage: EntityStorage, moduleName: String, vararg expectedRoots: Path) {
    assertContentRoots(virtualFileUrlManager, storage, moduleName, expectedRoots.asList())
  }

  @JvmStatic
  fun assertContentRoots(virtualFileUrlManager: VirtualFileUrlManager, storage: EntityStorage, moduleName: String, expectedRoots: List<Path>) {
    val moduleId = ModuleId(moduleName)
    val moduleEntity = storage.resolve(moduleId)
    Assertions.assertNotNull(moduleEntity) {
      "The module '$moduleName' doesn't exist"
    }
    assertContentRoots(virtualFileUrlManager, moduleEntity!!, expectedRoots)
  }

  @JvmStatic
  fun assertContentRoots(virtualFileUrlManager: VirtualFileUrlManager, moduleEntity: ModuleEntity, vararg expectedRoots: Path) {
    assertContentRoots(virtualFileUrlManager, moduleEntity, expectedRoots.asList())
  }

  @JvmStatic
  fun assertContentRoots(virtualFileUrlManager: VirtualFileUrlManager, moduleEntity: ModuleEntity, expectedRoots: List<Path>) {
    val expectedRootUrls = expectedRoots.map { it.normalize().toVirtualFileUrl(virtualFileUrlManager) }
    assertContentRoots(moduleEntity, expectedRootUrls)
  }

  @JvmStatic
  fun assertContentRoots(moduleEntity: ModuleEntity, vararg expectedRoots: VirtualFileUrl) {
    assertContentRoots(moduleEntity, expectedRoots.asList())
  }

  @JvmStatic
  fun assertContentRoots(moduleEntity: ModuleEntity, expectedRoots: List<VirtualFileUrl>) {
    val actualRoots = moduleEntity.contentRoots.map { it.url }
    CollectionAssertions.assertEqualsUnordered(expectedRoots, actualRoots)
  }
}