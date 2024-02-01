// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals

@TestApplication
@RunInEdt(writeIntent = true)
class LibraryInWorkspaceFileIndexTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  lateinit var root: VirtualFile
  lateinit var module: Module

  private val workspaceFileIndex
    get() = WorkspaceFileIndex.getInstance(projectModel.project) as WorkspaceFileIndexEx

  @BeforeEach
  internal fun setUp() {
    module = projectModel.createModule()
    root = projectModel.baseProjectDir.newVirtualDirectory("lib")
  }

  @Test
  fun `single library`() {
    val srcRoot = projectModel.baseProjectDir.newVirtualDirectory("lib-src")
    val docRoot = projectModel.baseProjectDir.newVirtualDirectory("lib-doc")
    val excludedRoot = projectModel.baseProjectDir.newVirtualDirectory("lib/lib-exc")
    val library = projectModel.addProjectLevelLibrary("lib") {
      it.addRoot(root, OrderRootType.CLASSES)
      it.addRoot(srcRoot, OrderRootType.SOURCES)
      it.addRoot(docRoot, OrderRootType.DOCUMENTATION)
      it.addExcludedRoot(excludedRoot.url)
    }
    ModuleRootModificationUtil.addDependency(module, library)
    val libraryId = findLibraryId(library)
    assertEquals(libraryId, findLibraries(root).single().symbolicId)
    assertEquals(libraryId, findLibraries(srcRoot).single().symbolicId)
    assertEquals(0, findLibraries(docRoot).size)
    assertEquals(0, findLibraries(excludedRoot).size)
  }
  
  @Test
  fun `one root in two libraries`() {
    val library = projectModel.addProjectLevelLibrary("lib") {
      it.addRoot(root, OrderRootType.CLASSES)
    }
    val library2 = projectModel.addProjectLevelLibrary("lib2") {
      it.addRoot(root, OrderRootType.CLASSES)
    }
    ModuleRootModificationUtil.addDependency(module, library)
    ModuleRootModificationUtil.addDependency(module, library2)
    val libraryId = findLibraryId(library)
    val library2Id = findLibraryId(library2)
    assertEquals(setOf(libraryId, library2Id), findLibraries(root).mapTo(HashSet()) { it.symbolicId })
  }
  
  @Test
  fun `nested libraries`() {
    val library = projectModel.addProjectLevelLibrary("lib") {
      it.addRoot(root, OrderRootType.CLASSES)
    }
    val root2 = projectModel.baseProjectDir.newVirtualDirectory("lib/nested")
    val library2 = projectModel.addProjectLevelLibrary("lib2") {
      it.addRoot(root2, OrderRootType.CLASSES)
    }
    ModuleRootModificationUtil.addDependency(module, library)
    ModuleRootModificationUtil.addDependency(module, library2)
    val libraryId = findLibraryId(library)
    val library2Id = findLibraryId(library2)
    assertEquals(libraryId, findLibraries(root).single().symbolicId)
    assertEquals(library2Id, findLibraries(root2).single().symbolicId)
  }

  private fun findLibraries(file: VirtualFile): Collection<LibraryEntity> = 
    workspaceFileIndex.findContainingEntities(file, LibraryEntity::class.java, true, 
                                              false, true, true, false)
}