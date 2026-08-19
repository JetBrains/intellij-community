// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.indexing.testEntities.IndexingTestEntity
import com.intellij.util.indexing.testEntities.NonIndexableTestEntity
import com.intellij.util.indexing.testEntities.NonRecursiveTestEntity
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleRelatedRootData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
internal class ModuleFileIndexImplTest {
  @RegisterExtension
  private val projectModel = ProjectModelExtension()

  @RegisterExtension
  private val tempDir = TempDirectoryExtension()

  private val disposable: Disposable get() = projectModel.disposableRule.disposable
  private val workspaceModel get() = projectModel.project.workspaceModel
  private val virtualFileUrlManager get() = workspaceModel.getVirtualFileUrlManager()

  private lateinit var module: Module

  @BeforeEach
  fun setUp() {
    module = projectModel.createModule()
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(ModuleContentFileSetContributor(module), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(ModuleNonRecursiveFileSetContributor(module), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(ModuleNonRecursiveNonIndexableFileSetContributor(module), disposable)
  }

  @Test
  fun `iterateContent visits non-indexable content`(): Unit = runBlocking {
    val nonIndexableRoot = tempDir.newVirtualDirectory("non-indexable")
    val nonIndexableFile = tempDir.newVirtualFile("non-indexable/non-indexable.txt")
    val indexableRoot = tempDir.newVirtualDirectory("non-indexable/indexable")
    val indexableFile = tempDir.newVirtualFile("non-indexable/indexable/indexable.txt")
    addFileSets(indexableRoots = listOf(indexableRoot), nonIndexableRoots = listOf(nonIndexableRoot))

    val result = iterateContent()

    assertThat(result.completed).isTrue()
    assertThat(result.files).containsExactlyInAnyOrder(nonIndexableRoot, nonIndexableFile, indexableRoot, indexableFile)
  }

  @Test
  fun `iterateIndexableContent skips non-indexable content and visits nested indexable content`(): Unit = runBlocking {
    val nonIndexableRoot = tempDir.newVirtualDirectory("non-indexable")
    val nonIndexableFile = tempDir.newVirtualFile("non-indexable/non-indexable.txt")
    val indexableRoot = tempDir.newVirtualDirectory("non-indexable/indexable")
    val indexableFile = tempDir.newVirtualFile("non-indexable/indexable/indexable.txt")
    addFileSets(indexableRoots = listOf(indexableRoot), nonIndexableRoots = listOf(nonIndexableRoot))

    val result = iterateIndexableContent()

    assertThat(result.completed).isTrue()
    assertThat(result.files).containsExactlyInAnyOrder(indexableRoot, indexableFile)
  }

  @Test
  fun `iterateIndexableContent visits indexable content under an excluded root`(): Unit = runBlocking {
    val excludedRoot = tempDir.newVirtualDirectory("excluded")
    tempDir.newVirtualFile("excluded/excluded.txt")
    val indexableRoot = tempDir.newVirtualDirectory("excluded/indexable")
    val indexableFile = tempDir.newVirtualFile("excluded/indexable/indexable.txt")
    workspaceModel.update { storage ->
      storage.addEntity(ExcludeUrlEntity(excludedRoot.toVirtualFileUrl(virtualFileUrlManager), NonPersistentEntitySource))
    }
    addFileSets(indexableRoots = listOf(indexableRoot), nonIndexableRoots = emptyList())

    val result = iterateIndexableContent()

    assertThat(result.completed).isTrue()
    assertThat(result.files).containsExactlyInAnyOrder(indexableRoot, indexableFile)
  }

  @Test
  fun `iterateIndexableContent applies the filter`(): Unit = runBlocking {
    val root = tempDir.newVirtualDirectory("root")
    val includedFile = tempDir.newVirtualFile("root/included.txt")
    val filteredFile = tempDir.newVirtualFile("root/filtered.txt")
    addFileSets(indexableRoots = listOf(root), nonIndexableRoots = emptyList())

    val result = iterateIndexableContent(VirtualFileFilter { file -> file != filteredFile })

    assertThat(result.completed).isTrue()
    assertThat(result.files).containsExactlyInAnyOrder(root, includedFile)
  }

  @Test
  fun `iterateIndexableContent does not visit overlapping recursive and non-recursive roots twice`(): Unit = runBlocking {
    val root = tempDir.newVirtualDirectory("root")
    val file = tempDir.newVirtualFile("root/file.txt")
    addFileSets(indexableRoots = listOf(root), nonIndexableRoots = emptyList())
    workspaceModel.update { storage ->
      storage.addEntity(NonRecursiveTestEntity(root.toVirtualFileUrl(virtualFileUrlManager), NonPersistentEntitySource))
    }

    val result = iterateIndexableContent()

    assertThat(result.completed).isTrue()
    assertThat(result.files).containsExactlyInAnyOrder(root, file)
  }

  @Test
  fun `iterateIndexableContent skips a non-indexable non-recursive root`(): Unit = runBlocking {
    val root = tempDir.newVirtualDirectory("root")
    tempDir.newVirtualFile("root/file.txt")
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(root.toVirtualFileUrl(virtualFileUrlManager), NonPersistentEntitySource))
    }

    val result = iterateIndexableContent()

    assertThat(result.completed).isTrue()
    assertThat(result.files).isEmpty()
  }

  @Test
  fun `iterateIndexableContent skips non-indexable non-recursive root while visiting indexable content`(): Unit = runBlocking {
    val indexableRoot = tempDir.newVirtualDirectory("indexable")
    val indexableFile = tempDir.newVirtualFile("indexable/indexable.txt")
    val nonIndexableRoot = tempDir.newVirtualDirectory("non-indexable")
    val nonIndexableFile = tempDir.newVirtualFile("non-indexable/non-indexable.txt")
    addFileSets(indexableRoots = listOf(indexableRoot), nonIndexableRoots = emptyList())
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(nonIndexableRoot.toVirtualFileUrl(virtualFileUrlManager), NonPersistentEntitySource))
    }

    val result = iterateIndexableContent()

    assertThat(result.completed).isTrue()
    assertThat(result.files).containsExactlyInAnyOrder(indexableRoot, indexableFile)
    assertThat(result.files).doesNotContain(nonIndexableRoot, nonIndexableFile)
  }

  @Test
  fun `iterateIndexableContent skips non-indexable non-recursive roots`(): Unit = runBlocking {
    val nonIndexableRoot = tempDir.newVirtualDirectory("non-indexable")
    val nonIndexableFile = tempDir.newVirtualFile("non-indexable/non-indexable.txt")
    addFileSets(indexableRoots = emptyList(), nonIndexableRoots = listOf(nonIndexableRoot))
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(nonIndexableRoot.toVirtualFileUrl(virtualFileUrlManager), NonPersistentEntitySource))
    }

    val indexableResult = iterateIndexableContent()
    val contentResult = iterateContent()

    assertThat(indexableResult.completed).isTrue()
    assertThat(indexableResult.files).isEmpty()
    assertThat(contentResult.completed).isTrue()
    assertThat(contentResult.files).containsExactlyInAnyOrder(nonIndexableRoot, nonIndexableFile)
  }

  @Test
  fun `iterateIndexableContent returns false when processing is stopped`(): Unit = runBlocking {
    val root = tempDir.newVirtualDirectory("root")
    tempDir.newVirtualFile("root/file.txt")
    addFileSets(indexableRoots = listOf(root), nonIndexableRoots = emptyList())

    val result = iterateIndexableContent(processor = { false })

    assertThat(result.completed).isFalse()
    assertThat(result.files).hasSize(1)
  }

  private suspend fun addFileSets(indexableRoots: List<VirtualFile>, nonIndexableRoots: List<VirtualFile>) {
    workspaceModel.update { storage ->
      storage.addEntity(
        IndexingTestEntity(
          indexableRoots.map { it.toVirtualFileUrl(virtualFileUrlManager) },
          nonIndexableRoots.map { it.toVirtualFileUrl(virtualFileUrlManager) },
          NonPersistentEntitySource,
        )
      )
    }
  }

  private fun iterateContent(
    filter: VirtualFileFilter? = null,
    processor: (VirtualFile) -> Boolean = { true },
  ): IterationResult {
    val files = mutableListOf<VirtualFile>()
    val completed = ModuleRootManager.getInstance(module).fileIndex.iterateContent(
      ContentIterator { file ->
        files.add(file)
        processor(file)
      },
      filter,
    )
    return IterationResult(completed, files)
  }

  private fun iterateIndexableContent(
    filter: VirtualFileFilter? = null,
    processor: (VirtualFile) -> Boolean = { true },
  ): IterationResult {
    val files = mutableListOf<VirtualFile>()
    val completed = ModuleRootManager.getInstance(module).fileIndex.iterateIndexableContent(
      ContentIterator { file ->
        files.add(file)
        processor(file)
      },
      filter,
    )
    return IterationResult(completed, files)
  }

  private data class IterationResult(val completed: Boolean, val files: List<VirtualFile>)

  private class ModuleContentFileSetContributor(private val module: Module) : WorkspaceFileIndexContributor<IndexingTestEntity> {
    override val entityClass: Class<IndexingTestEntity> = IndexingTestEntity::class.java

    override fun registerFileSets(entity: IndexingTestEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      for (root in entity.roots) {
        registrar.registerFileSet(root, WorkspaceFileKind.CONTENT, entity, ModuleRootData(module))
      }
      for (root in entity.excludedRoots) {
        registrar.registerFileSet(root, WorkspaceFileKind.CONTENT_NON_INDEXABLE, entity, ModuleRootData(module))
      }
    }
  }

  private class ModuleNonRecursiveFileSetContributor(private val module: Module) : WorkspaceFileIndexContributor<NonRecursiveTestEntity> {
    override val entityClass: Class<NonRecursiveTestEntity> = NonRecursiveTestEntity::class.java

    override fun registerFileSets(entity: NonRecursiveTestEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      registrar.registerNonRecursiveFileSet(entity.root, WorkspaceFileKind.CONTENT, entity, ModuleRootData(module))
    }
  }

  private class ModuleNonRecursiveNonIndexableFileSetContributor(private val module: Module) : WorkspaceFileIndexContributor<NonIndexableTestEntity> {
    override val entityClass: Class<NonIndexableTestEntity> = NonIndexableTestEntity::class.java

    override fun registerFileSets(entity: NonIndexableTestEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
      registrar.registerNonRecursiveFileSet(entity.root, WorkspaceFileKind.CONTENT_NON_INDEXABLE, entity, ModuleRootData(module))
    }
  }

  private data class ModuleRootData(override val module: Module) : ModuleRelatedRootData
}
