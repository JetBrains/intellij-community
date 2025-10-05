// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.assertIteratedContent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.indexing.testEntities.IndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.IndexingTestEntity
import com.intellij.util.indexing.testEntities.NonIndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.NonIndexableTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class NonIndexableFileSetTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @JvmField
  @RegisterExtension
  val baseNonProjectDir: TempDirectoryExtension = TempDirectoryExtension()

  private val fileIndex
    get() = WorkspaceFileIndex.getInstance(projectModel.project)

  private val projectFileIndex
    get() = ProjectFileIndex.getInstance(projectModel.project)

  @TestDisposable
  private lateinit var disposable: Disposable
  private lateinit var customContentFileSetRoot: VirtualFile
  private lateinit var module: Module

  @BeforeEach
  fun setUp() {
    customContentFileSetRoot = baseNonProjectDir.newVirtualDirectory("root")
    module = projectModel.createModule()
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonIndexableKindFileSetTestContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(IndexableKindFileSetTestContributor(), disposable)
  }

  @Test
  fun `add a non indexable entity`() = runBlocking {
    val file = baseNonProjectDir.newVirtualFile("root/a.txt")
    val root = file.parent
    readAction {
      assertFalse(fileIndex.isInWorkspace(root))
      assertFalse(fileIndex.isInContent(root))
      assertFalse(fileIndex.isIndexable(root))
      assertIteratedContent(projectModel.project, mustNotContain = listOf(root, file))
    }

    val workspaceModel = projectModel.project.serviceAsync<WorkspaceModel>()
    workspaceModel.update {
      val url = root.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager())
      it.addEntity(NonIndexableTestEntity(url, NonPersistentEntitySource))
    }

    readAction {
      assertTrue(fileIndex.isInWorkspace(file))
      assertTrue(fileIndex.isInContent(file))
      assertFalse(fileIndex.isIndexable(root))
      assertTrue(projectFileIndex.isInProjectOrExcluded(file))
      assertIteratedContent(projectModel.project, mustContain = listOf(root, file))
      val fileSet = fileIndex.findFileSet(file, true, true, true, true, true, true)
      assertNotNull(fileSet)
      assertEquals(WorkspaceFileKind.CONTENT_NON_INDEXABLE, fileSet!!.kind)
    }
  }

  @Test
  fun `add a indexable and non indexable entity for the same file`() = runBlocking {
    val file = baseNonProjectDir.newVirtualFile("root/a.txt")
    val root = file.parent

    val workspaceModel = projectModel.project.serviceAsync<WorkspaceModel>()
    workspaceModel.update {
      val url = root.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager())
      it.addEntity(NonIndexableTestEntity(url, NonPersistentEntitySource))
      it.addEntity(IndexingTestEntity(listOf(url), emptyList(), NonPersistentEntitySource))
      it.addEntity(NonIndexableTestEntity(url, NonPersistentEntitySource))
    }

    readAction {
      assertTrue(fileIndex.isInWorkspace(file))
      assertTrue(fileIndex.isInContent(file))
      assertTrue(projectFileIndex.isInProjectOrExcluded(file))
      assertTrue(fileIndex.isIndexable(root))
    }
  }

  @Test
  fun `if parent directory is indexable, child also needs to be indexable`() = runBlocking {
    val file = baseNonProjectDir.newVirtualFile("indexable/non_indexable/a.txt")
    val nonIndexable = file.parent
    val indexable = file.parent.parent

    val workspaceModel = projectModel.project.serviceAsync<WorkspaceModel>()
    val virtualFileManager = workspaceModel.getVirtualFileUrlManager()
    workspaceModel.update {
      it.addEntity(NonIndexableTestEntity(nonIndexable.toVirtualFileUrl(virtualFileManager), NonPersistentEntitySource))
      it.addEntity(IndexingTestEntity(listOf(indexable.toVirtualFileUrl(virtualFileManager)), emptyList(), NonPersistentEntitySource))
    }

    readAction {
      assertTrue(fileIndex.isIndexable(file))
    }
  }

}