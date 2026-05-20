// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.ThreeState
import com.intellij.util.indexing.testEntities.IndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.IndexingTestEntity
import com.intellij.util.indexing.testEntities.NonRecursiveFileCustomData
import com.intellij.util.indexing.testEntities.NonRecursiveFileSetContributor
import com.intellij.util.indexing.testEntities.NonRecursiveTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.io.path.invariantSeparatorsPathString

@TestApplication
class NonRecursiveWorkspaceFileSetTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @TestDisposable
  private lateinit var disposable: Disposable

  private val fileIndex
    get() = WorkspaceFileIndex.getInstance(projectModel.project)

  private lateinit var module: Module
  private lateinit var excludedRoot: VirtualFile

  @BeforeEach
  fun setUp() = runBlocking {
    edtWriteAction {
      module = projectModel.createModule()
      excludedRoot = projectModel.baseProjectDir.newVirtualDirectory("root/exc")
      ModuleRootModificationUtil.addContentRoot(module, excludedRoot.parent)
      PsiTestUtil.addExcludedRoot(module, excludedRoot)
    }
  }

  @Test
  fun `non-recursive file set`() = runBlocking {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonRecursiveFileSetContributor(), disposable)

    val file = projectModel.baseProjectDir.newVirtualFile("root/nonRecursiveDir/a.txt")
    val nonRecursiveDir = file.parent

    val workspaceModel = projectModel.project.serviceAsync<WorkspaceModel>()
    val workspaceFileIndex = WorkspaceFileIndex.getInstance(projectModel.project)
    workspaceModel.update {
      val url = nonRecursiveDir.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager())
      it.addEntity(NonRecursiveTestEntity(url, NonPersistentEntitySource))
    }
    readAction {
      val fileSet = workspaceFileIndex.findFileSetWithCustomData(file, true, true, true, true, true, true, true, NonRecursiveFileCustomData::class.java)
      assertNull(fileSet)
      val nonRecursiveDirFileSet = workspaceFileIndex.findFileSetWithCustomData(nonRecursiveDir, true, true, true, true, true, true, true, NonRecursiveFileCustomData::class.java)
      assertNotNull(nonRecursiveDirFileSet)
    }
  }

  @Test
  fun `non-existing non-recursive file set is in content only by exact url`() = runBlocking {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonRecursiveFileSetContributor(), disposable)

    val workspaceModel = projectModel.project.serviceAsync<WorkspaceModel>()
    val rootUrl = VfsUtilCore.pathToUrl(projectModel.baseProjectDir.rootPath.resolve("nonRecursiveRoot").invariantSeparatorsPathString)
    workspaceModel.update {
      val url = workspaceModel.getVirtualFileUrlManager().getOrCreateFromUrl(rootUrl)
      it.addEntity(NonRecursiveTestEntity(url, NonPersistentEntitySource))
    }

    readAction {
      assertEquals(ThreeState.YES, fileIndex.isUrlInContent(rootUrl))
      assertEquals(ThreeState.NO, fileIndex.isUrlInContent("$rootUrl/a.txt"))
    }
  }

  @Test
  fun `non-recursive and recursive file set on one directory`() = runBlocking {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonRecursiveFileSetContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(IndexableKindFileSetTestContributor(), disposable)

    val file = projectModel.baseProjectDir.newVirtualFile("root/nonRecursiveDir/a.txt")
    val nonRecursiveDir = file.parent

    val workspaceModel = projectModel.project.serviceAsync<WorkspaceModel>()
    val workspaceFileIndex = WorkspaceFileIndex.getInstance(projectModel.project)
    workspaceModel.update {
      val url = nonRecursiveDir.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager())
      it.addEntity(IndexingTestEntity(listOf(url), emptyList(), NonPersistentEntitySource))
      it.addEntity(NonRecursiveTestEntity(url, NonPersistentEntitySource))
    }
    readAction {
      val fileSet = workspaceFileIndex.findFileSetWithCustomData(file, true, true, true, true, true, true, true, NonRecursiveFileCustomData::class.java)
      assertNull(fileSet)
      val nonRecursiveDirFileSet = workspaceFileIndex.findFileSetWithCustomData(nonRecursiveDir, true, true, true, true, true, true, true, NonRecursiveFileCustomData::class.java)
      assertNotNull(nonRecursiveDirFileSet)
    }
  }

  @Test
  fun `non-recursive file set inside excluded dir`() = runBlocking {
    val file = projectModel.baseProjectDir.newVirtualFile("root/a.txt")
    val file2 = projectModel.baseProjectDir.newVirtualDirectory("root/exc/non-recursive-dir/b.txt")
    val nonRecursiveDir = file2.parent

    val workspaceModel = projectModel.project.serviceAsync<WorkspaceModel>()
    workspaceModel.update {
      val url = nonRecursiveDir.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager())
      it.addEntity(NonRecursiveTestEntity(url, NonPersistentEntitySource))
    }
    readAction {
      assertTrue(fileIndex.isInContent(file))
      assertFalse(fileIndex.isInContent(nonRecursiveDir))
      assertFalse(fileIndex.isInContent(file2))
    }

    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonRecursiveFileSetContributor(), disposable)

    readAction {
      assertTrue(fileIndex.isInContent(file))
      assertTrue(fileIndex.isInContent(nonRecursiveDir))
      assertFalse(fileIndex.isInContent(file2))
    }
  }
}