// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.sourceRoots
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.junit.Before
import org.junit.Test
import java.io.File

class JpsIncorrectDataLoading : HeavyPlatformTestCase() {

  private lateinit var errorCollector: CollectingErrorReporter

  @Before
  override fun setUp() {
    super.setUp()
    errorCollector = CollectingErrorReporter()
  }

  @Test
  fun `test load broken library order entry`() {
    val projectDir = PathManagerEx.findFileUnderCommunityHome(
      "platform/workspace/jps/tests/testData/serialization/brokenRoots/libraryOrderEntry")
    val storage = loadProject(projectDir)
    val modules = storage.entities(ModuleEntity::class.java).toList()
    assertEquals(1, modules.size)
    assertEquals(1, errorCollector.messages.size)
  }

  @Test
  fun `test load broken library order entry multiple`() {
    val projectDir = PathManagerEx.findFileUnderCommunityHome(
      "platform/workspace/jps/tests/testData/serialization/brokenRoots/multipleLibraryOrderEntry")
    val storage = loadProject(projectDir)
    val modules = storage.entities(ModuleEntity::class.java).toList()
    assertEquals(1, modules.size)
    assertEquals(3, modules.single().dependencies.size)
    assertTrue(errorCollector.messages.isNotEmpty())
  }

  @Test
  fun `test mess in source folder`() {
    val projectDir = PathManagerEx.findFileUnderCommunityHome(
      "platform/workspace/jps/tests/testData/serialization/brokenRoots/sourceFolder")
    val storage = loadProject(projectDir)
    val modules = storage.entities(ModuleEntity::class.java).toList()
    assertEquals(1, modules.size)
    assertTrue(errorCollector.messages.isNotEmpty())
  }

  @Test
  fun `test mess in broken url`() {
    val projectDir = PathManagerEx.findFileUnderCommunityHome(
      "platform/workspace/jps/tests/testData/serialization/brokenRoots/sourceFolderBrokenUrl")
    val storage = loadProject(projectDir)
    val modules = storage.entities(ModuleEntity::class.java).toList()
    assertEquals(1, modules.size)
    assertEquals(1, modules.single().sourceRoots.size)
    assertTrue(errorCollector.messages.isEmpty())
  }

  @Test
  fun `test mess in source folder - is test source`() {
    val projectDir = PathManagerEx.findFileUnderCommunityHome(
      "platform/workspace/jps/tests/testData/serialization/brokenRoots/sourceFolderBrokenIsTestSource")
    val storage = loadProject(projectDir)
    val modules = storage.entities(ModuleEntity::class.java).toList()
    assertEquals(1, modules.size)
    assertEquals(1, modules.single().sourceRoots.size)
    assertTrue(errorCollector.messages.isEmpty())
  }

  private fun loadProject(projectFile: File): EntityStorage {
    val storageBuilder = MutableEntityStorage.create()
    val virtualFileManager: VirtualFileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    loadProject(projectFile.asConfigLocation(virtualFileManager), storageBuilder, storageBuilder, virtualFileManager,
                errorReporter = errorCollector)
    return storageBuilder.toSnapshot()
  }
}