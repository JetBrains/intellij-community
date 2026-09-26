// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.indexing.testEntities.IndexingTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class CustomExternalFileSetTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @TestDisposable
  private lateinit var disposable: Disposable
  private lateinit var contentRoot: VirtualFile
  private lateinit var module: Module

  @BeforeEach
  fun setUp() {
    contentRoot = projectModel.baseProjectDir.newVirtualDirectory("root")
    module = projectModel.createModule()
    PsiTestUtil.addSourceContentToRoots(module, contentRoot)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(CustomExternalFileSetContributor(), disposable)
  }

  @Test
  fun `add and remove entity with custom external file set`() = runBlocking {
    val file = projectModel.baseProjectDir.newVirtualFile("root/excluded/external/a.txt")
    val externalRoot = file.parent
    val excludedRoot = externalRoot.parent
    readAction { 
      assertEquals(module, ModuleUtilCore.findModuleForFile(file, projectModel.project))
    }
    
    WorkspaceModel.getInstance(projectModel.project).update {
      val virtualFileManager = WorkspaceModel.getInstance(projectModel.project).getVirtualFileUrlManager()
      val url = externalRoot.toVirtualFileUrl(virtualFileManager)
      val excludedUrl = excludedRoot.toVirtualFileUrl(virtualFileManager)
      it.addEntity(IndexingTestEntity(listOf(url), listOf(excludedUrl), NonPersistentEntitySource))
    }

    readAction {
      assertNull(ModuleUtilCore.findModuleForFile(file, projectModel.project))
      val psiFile = psiManager.findFile(file)!!
      assertNull(ModuleUtilCore.findModuleForPsiElement(psiFile))
      assertNull(ModuleUtilCore.findModuleForPsiElement(psiFile.parent!!))
    }

    WorkspaceModel.getInstance(projectModel.project).update {
      it.removeEntity(it.entities(IndexingTestEntity::class.java).single())
    }

    readAction {
      assertEquals(module, ModuleUtilCore.findModuleForFile(file, projectModel.project))
    }
  }

  private val psiManager get() = PsiManager.getInstance(projectModel.project)

  private class CustomExternalFileSetContributor : WorkspaceFileIndexContributor<IndexingTestEntity> {
    override val entityClass: Class<IndexingTestEntity>
      get() = IndexingTestEntity::class.java

    override fun registerFileSets(entity: IndexingTestEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      for (root in entity.roots) {
        registrar.registerFileSet(root, WorkspaceFileKind.EXTERNAL, entity, null)
      }
      for (excludedRoot in entity.excludedRoots) {
        registrar.registerExcludedRoot(excludedRoot, entity)
      }
    }
  }
}