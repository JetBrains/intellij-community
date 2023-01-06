// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.junit.Test
import java.io.File

class JpsIncorrectDataLoading  : HeavyPlatformTestCase() {
  @Test
  fun `test load broken library order entry`() {
    val projectDir = PathManagerEx.findFileUnderCommunityHome("platform/workspaceModel/jps/tests/testData/serialization/brokenRoots/libraryOrderEntry")
    val storage = loadProject(projectDir)
    val modules = storage.entities(ModuleEntity::class.java).toList()
    assertEquals(1, modules.size)
  }

  private fun loadProject(projectFile: File): EntityStorage {
    val storageBuilder = MutableEntityStorage.create()
    val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
    loadProject(projectFile.asConfigLocation(virtualFileManager), storageBuilder, virtualFileManager, errorReporter = SilentErrorReporter)
    return storageBuilder.toSnapshot()
  }
}