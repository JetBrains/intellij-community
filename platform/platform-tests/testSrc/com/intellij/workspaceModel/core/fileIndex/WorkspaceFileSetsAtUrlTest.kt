// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.indexing.testEntities.IndexingTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceExcludeFileSet
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
internal class WorkspaceFileSetsAtUrlTest {
  @RegisterExtension
  private val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val disposable get() = projectModel.disposableRule.disposable

  @Test
  fun `virtual file registration stores the root URL`(): Unit = timeoutRunBlocking {
    val project = projectModel.project
    val workspaceModel = project.workspaceModel
    val urlManager = workspaceModel.getVirtualFileUrlManager()
    val root = LightVirtualFile("root")
    assertNull(urlManager.get(root.url))
    val contributor = object : WorkspaceFileIndexContributor<IndexingTestEntity> {
      override val entityClass: Class<IndexingTestEntity> = IndexingTestEntity::class.java

      override fun registerFileSets(entity: IndexingTestEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
        registrar.registerFileSet(root, WorkspaceFileKind.EXTERNAL, entity, null)
      }
    }
    edtWriteAction {
      WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(contributor, disposable)
    }
    val index = WorkspaceFileIndexEx.getInstance(project)
    workspaceModel.update { storage ->
      storage.addEntity(IndexingTestEntity(emptyList(), emptyList(), NonPersistentEntitySource))
    }
    assertNotNull(urlManager.get(root.url))
    readAction {
      assertNotEquals(0, index.indexData.getFileSetsAt(root).mask)
    }
    edtWriteAction {
      index.reset()
    }
    index.initialize()
    readAction {
      assertNotEquals(0, index.indexData.getFileSetsAt(root).mask)
    }
    workspaceModel.update { storage ->
      storage.removeEntity(storage.entities(IndexingTestEntity::class.java).single())
    }
    readAction {
      assertEquals(0, index.indexData.getFileSetsAt(root).mask)
    }
  }

  @Test
  fun `missing URL lookup refreshes policy exclusions`(): Unit = runBlocking {
    lateinit var index: WorkspaceFileIndexEx
    lateinit var firstUrl: VirtualFileUrl
    lateinit var secondUrl: VirtualFileUrl
    lateinit var excludedUrls: Array<String>
    edtWriteAction {
      val project = projectModel.project
      val urlManager = project.workspaceModel.getVirtualFileUrlManager()
      val root = projectModel.baseProjectDir.newVirtualDirectory("root")
      firstUrl = urlManager.storeAndGet(root.url + "/first-missing")
      secondUrl = urlManager.storeAndGet(root.url + "/second-missing")
      excludedUrls = arrayOf(firstUrl.url)
      val policy = object : DirectoryIndexExcludePolicy {
        override fun getExcludeUrlsForProject(): Array<String> = excludedUrls
      }
      DirectoryIndexExcludePolicy.EP_NAME.getPoint(project).registerExtension(policy, disposable)
      index = WorkspaceFileIndexEx.getInstance(project)
      index.reset()
    }

    readAction {
      val sets = index.getFileSetsAt(firstUrl)
      assertEquals(0, sets.mask)
      assertEquals(0, sets.maskRecursive)
      val exclusion = sets.excludes.single()
      assertTrue(exclusion is WorkspaceExcludeFileSet.ByFileKind)
    }

    edtWriteAction {
      excludedUrls = arrayOf(secondUrl.url)
      index.indexData.resetCustomContributors()
    }

    readAction {
      assertTrue(index.getFileSetsAt(firstUrl).excludes.isEmpty())
      val exclusion = index.getFileSetsAt(secondUrl).excludes.single()
      assertTrue(exclusion is WorkspaceExcludeFileSet.ByFileKind)
    }
  }

  @Test
  fun `missing URL exclusions ByPattern and ByCondition work on FakeVirtualFile`(): Unit = runBlocking {
    lateinit var index: WorkspaceFileIndexEx
    lateinit var rootUrl: VirtualFileUrl
    lateinit var missingUrl: VirtualFileUrl
    lateinit var root: VirtualFile
    edtWriteAction {
      val project = projectModel.project
      root = projectModel.baseProjectDir.newVirtualDirectory("root")
      ModuleRootModificationUtil.addContentRoot(projectModel.createModule(), root)
      val urlManager = project.workspaceModel.getVirtualFileUrlManager()
      rootUrl = urlManager.storeAndGet(root.url)
      missingUrl = urlManager.storeAndGet(root.url + "/" + MISSING_DIR_NAME)
      WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(ExcludeInMissingDirContributor(), disposable)
      index = WorkspaceFileIndexEx.getInstance(project)
      index.reset()
    }

    readAction {
      val contentMask = index.getFileSetsAt(rootUrl).mask
      assertNotEquals(0, contentMask)

      val excludes = index.getFileSetsAt(missingUrl).excludes
      assertEquals(2, excludes.size)
      val byPattern = excludes.filterIsInstance<WorkspaceExcludeFileSet.ByPattern>().single()
      val byCondition = excludes.filterIsInstance<WorkspaceExcludeFileSet.ByCondition>().single()

      val missingDir = FakeVirtualFile(root, MISSING_DIR_NAME)
      val matchingFile = FakeVirtualFile(missingDir, "a.log")
      val otherFile = FakeVirtualFile(missingDir, "a.txt")
      val conditionFile = FakeVirtualFile(missingDir, EXCLUDED_FILE_NAME)

      assertEquals(0, byPattern.inPlaceComputeMasks(matchingFile, contentMask))
      assertEquals(contentMask, byPattern.inPlaceComputeMasks(otherFile, contentMask))
      assertEquals(contentMask, byPattern.inPlaceComputeMasks(conditionFile, contentMask))

      assertEquals(0, byCondition.inPlaceComputeMasks(conditionFile, contentMask))
      assertEquals(contentMask, byCondition.inPlaceComputeMasks(matchingFile, contentMask))
      assertEquals(contentMask, byCondition.inPlaceComputeMasks(otherFile, contentMask))
    }
  }

  /** Registers a pattern and a condition at a directory below each content root. The directory does not exist. */
  private class ExcludeInMissingDirContributor : WorkspaceFileIndexContributor<ContentRootEntity> {
    override val entityClass: Class<ContentRootEntity>
      get() = ContentRootEntity::class.java

    override fun registerFileSets(entity: ContentRootEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      val missingDir = entity.url.append(MISSING_DIR_NAME)
      registrar.registerExclusionPatterns(missingDir, listOf("*.log"), entity)
      registrar.registerExclusionCondition(missingDir, ExcludeByNameCondition, entity)
    }
  }

  private object ExcludeByNameCondition : WorkspaceFileSetExclusionCondition {
    override fun shouldExclude(file: VirtualFile): Boolean = file.name == EXCLUDED_FILE_NAME

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = EXCLUDED_FILE_NAME.hashCode()
  }

  companion object {
    private const val MISSING_DIR_NAME = "missing"
    private const val EXCLUDED_FILE_NAME = "excluded.txt"
  }
}
