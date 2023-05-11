// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.roots.impl.assertIteratedContent
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.workspaceModel.updateProjectModelAsync
import com.intellij.util.indexing.testEntities.IndexingTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.toVirtualFileUrl
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestApplication
class CustomContentFileSetTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val fileIndex
    get() = WorkspaceFileIndex.getInstance(projectModel.project)

  @TestDisposable
  private lateinit var disposable: Disposable
  private lateinit var customContentFileSetRoot: VirtualFile

  @BeforeEach
  fun setUp() {
    customContentFileSetRoot = projectModel.baseProjectDir.newVirtualDirectory("root")
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(CustomContentFileSetContributor(), disposable)
  }

  @Test
  fun `add and remove entity with custom content file set`() = runBlocking {
    val file = projectModel.baseProjectDir.newVirtualFile("root/a.txt")
    val root = file.parent
    readAction {
      assertFalse(fileIndex.isInContent(root))
      assertIteratedContent(projectModel.project, mustNotContain = listOf(root, file))
    }

    WorkspaceModel.getInstance(projectModel.project).updateProjectModelAsync {
      val url = root.toVirtualFileUrl(VirtualFileUrlManager.getInstance(projectModel.project))
      it.addEntity(IndexingTestEntity(listOf(url), emptyList(), NonPersistentEntitySource))
    }

    readAction {
      assertTrue(fileIndex.isInContent(file))
      assertIteratedContent(projectModel.project, mustContain = listOf(root, file))
    }

    WorkspaceModel.getInstance(projectModel.project).updateProjectModelAsync {
      it.removeEntity(it.entities(IndexingTestEntity::class.java).single())
    }

    readAction {
      assertFalse(fileIndex.isInContent(root))
      assertIteratedContent(projectModel.project, mustNotContain = listOf(root, file))
    }
  }
  
  private class CustomContentFileSetContributor : WorkspaceFileIndexContributor<IndexingTestEntity> {
    override val entityClass: Class<IndexingTestEntity>
      get() = IndexingTestEntity::class.java

    override fun registerFileSets(entity: IndexingTestEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      for (root in entity.roots) {
        registrar.registerFileSet(root, WorkspaceFileKind.CONTENT, entity, null)
      }
    }
  }
}