// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion.moduleAssertion

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.junit.jupiter.api.Assertions
import java.nio.file.Path
import java.util.ArrayList

object SourceRootAssertions {

  @JvmStatic
  fun assertSourceRoots(
    module: Module,
    filter: (SourceRootEntity) -> Boolean,
    vararg expectedRoots: Path,
    messageSupplier: (() -> String)? = null,
  ) {
    assertSourceRoots(module, filter, expectedRoots.asList(), messageSupplier)
  }

  @JvmStatic
  fun assertSourceRoots(
    module: Module,
    filter: (SourceRootEntity) -> Boolean,
    expectedRoots: List<Path>,
    messageSupplier: (() -> String)? = null,
  ) {
    assertSourceRoots(module.project, module.name, filter, expectedRoots, messageSupplier)
  }

  @JvmStatic
  fun assertSourceRoots(
    project: Project,
    moduleName: String,
    filter: (SourceRootEntity) -> Boolean,
    vararg expectedRoots: Path,
    messageSupplier: (() -> String)? = null,
  ) {
    assertSourceRoots(project, moduleName, filter, expectedRoots.asList(), messageSupplier)
  }

  @JvmStatic
  fun assertSourceRoots(
    project: Project,
    moduleName: String,
    filter: (SourceRootEntity) -> Boolean,
    expectedRoots: List<Path>,
    messageSupplier: (() -> String)? = null,
  ) {
    val workspaceModel = project.workspaceModel
    val storage = workspaceModel.currentSnapshot
    val virtualFileUrlManager = workspaceModel.getVirtualFileUrlManager()
    assertSourceRoots(virtualFileUrlManager, storage, moduleName, filter, expectedRoots, messageSupplier)
  }

  @JvmStatic
  fun assertSourceRoots(
    virtualFileUrlManager: VirtualFileUrlManager,
    storage: EntityStorage,
    moduleName: String,
    filter: (SourceRootEntity) -> Boolean,
    vararg expectedRoots: Path,
    messageSupplier: (() -> String)? = null,
  ) {
    assertSourceRoots(virtualFileUrlManager, storage, moduleName, filter, expectedRoots.asList(), messageSupplier)
  }

  @JvmStatic
  fun assertSourceRoots(
    virtualFileUrlManager: VirtualFileUrlManager,
    storage: EntityStorage,
    moduleName: String,
    filter: (SourceRootEntity) -> Boolean,
    expectedRoots: List<Path>,
    messageSupplier: (() -> String)? = null,
  ) {
    val moduleId = ModuleId(moduleName)
    val moduleEntity = storage.resolve(moduleId)
    Assertions.assertNotNull(moduleEntity) {
      "The module '$moduleName' doesn't exist"
    }
    assertSourceRoots(virtualFileUrlManager, moduleEntity!!, filter, expectedRoots, messageSupplier)
  }

  @JvmStatic
  fun assertSourceRoots(
    virtualFileUrlManager: VirtualFileUrlManager,
    moduleEntity: ModuleEntity,
    filter: (SourceRootEntity) -> Boolean,
    vararg expectedRoots: Path,
    messageSupplier: (() -> String)? = null,
  ) {
    assertSourceRoots(virtualFileUrlManager, moduleEntity, filter, expectedRoots.asList(), messageSupplier)
  }

  @JvmStatic
  fun assertSourceRoots(
    virtualFileUrlManager: VirtualFileUrlManager,
    moduleEntity: ModuleEntity,
    filter: (SourceRootEntity) -> Boolean,
    expectedRoots: List<Path>,
    messageSupplier: (() -> String)? = null,
  ) {
    val expectedRootUrls = expectedRoots.map { it.normalize().toVirtualFileUrl(virtualFileUrlManager) }
    assertSourceRoots(moduleEntity, filter, expectedRootUrls, messageSupplier)
  }

  @JvmStatic
  fun assertSourceRoots(
    moduleEntity: ModuleEntity,
    filter: (SourceRootEntity) -> Boolean,
    vararg expectedRoots: VirtualFileUrl,
    messageSupplier: (() -> String)? = null,
  ) {
    assertSourceRoots(moduleEntity, filter, expectedRoots.asList(), messageSupplier)
  }

  @JvmStatic
  fun assertSourceRoots(
    moduleEntity: ModuleEntity,
    filter: (SourceRootEntity) -> Boolean,
    expectedRoots: List<VirtualFileUrl>,
    messageSupplier: (() -> String)? = null,
  ) {
    val actualRoots = ArrayList<VirtualFileUrl>()
    for (contentRoot in moduleEntity.contentRoots) {
      for (sourceRoot in contentRoot.sourceRoots) {
        if (filter(sourceRoot)) {
          actualRoots.add(sourceRoot.url)
        }
      }
    }
    assertEqualsUnordered(actualRoots, expectedRoots, messageSupplier)
  }
}
