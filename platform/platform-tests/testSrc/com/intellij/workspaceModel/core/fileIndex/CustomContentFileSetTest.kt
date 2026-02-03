// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.assertIteratedContent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.indexing.testEntities.IndexingTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleRelatedRootData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class CustomContentFileSetTest {
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
    customContentFileSetRoot = projectModel.baseProjectDir.newVirtualDirectory("root")
    module = projectModel.createModule()
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(CustomContentFileSetContributor(module), disposable)
  }

  @Test
  fun `add and remove entity with custom content file set`() = runBlocking {
    val file = baseNonProjectDir.newVirtualFile("root/a.txt")
    val root = file.parent
    readAction {
      assertFalse(fileIndex.isInContent(root))
      assertIteratedContent(projectModel.project, mustNotContain = listOf(root, file))
    }

    WorkspaceModel.getInstance(projectModel.project).update {
      val url = root.toVirtualFileUrl(WorkspaceModel.getInstance(projectModel.project).getVirtualFileUrlManager())
      it.addEntity(IndexingTestEntity(listOf(url), emptyList(), NonPersistentEntitySource))
    }

    readAction {
      assertTrue(fileIndex.isInContent(file))
      assertEquals(module, projectFileIndex.getModuleForFile(file))
      assertTrue(module.moduleContentScope.contains(file))
      assertFalse(module.moduleScope.contains(file))
      assertIteratedContent(projectModel.project, mustContain = listOf(root, file))
    }

    WorkspaceModel.getInstance(projectModel.project).update {
      it.removeEntity(it.entities(IndexingTestEntity::class.java).single())
    }

    readAction {
      assertFalse(fileIndex.isInContent(root))
      assertNull(projectFileIndex.getModuleForFile(file))
      assertFalse(module.moduleContentScope.contains(file))
      assertIteratedContent(projectModel.project, mustNotContain = listOf(root, file))
    }
  }
  
  private class CustomContentFileSetContributor(private val module: Module) : WorkspaceFileIndexContributor<IndexingTestEntity> {
    override val entityClass: Class<IndexingTestEntity>
      get() = IndexingTestEntity::class.java

    override fun registerFileSets(entity: IndexingTestEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      for (root in entity.roots) {
        registrar.registerFileSet(root, WorkspaceFileKind.CONTENT, entity, CustomModuleRelatedRootData(module))
      }
    }
  }
  
  private class CustomModuleRelatedRootData(override val module: Module): ModuleRelatedRootData
}