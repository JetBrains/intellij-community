// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl.indexing

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.ProjectIndexableFilesIteratorImpl
import com.intellij.util.indexing.roots.origin.IndexingUrlRootHolder
import com.intellij.util.indexing.testEntities.NonIndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.NonIndexableTestEntity
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.pathString
import kotlin.io.path.writeText

@TestApplication
internal class IndexableFilesNoCacheAvoidingTest {

  @RegisterExtension
  val projectModel = ProjectModelExtension()

  @JvmField
  @RegisterExtension
  val tempDir = TempDirectoryExtension()

  private val disposable: Disposable get() = projectModel.disposableRule.disposable

  private val project get() = projectModel.project
  private val workspaceFileIndex get() = WorkspaceFileIndex.getInstance(project)
  private val workspaceModel get() = project.workspaceModel
  private val vfuManager get() = workspaceModel.getVirtualFileUrlManager()

  private lateinit var module: Module
  private lateinit var nonIndexableRoot: VirtualFile
  private lateinit var indexableRootOne: VirtualFile
  private lateinit var indexableRootTwo: VirtualFile

  private val expectedIndexableFiles = listOf(
    "indexable-one",
    "indexable-one/one.txt",
    "indexable-two",
    "indexable-two/two.txt",
  )

  private val expectedNonRecursiveIndexableFiles = listOf(
    "indexable-one",
    "indexable-two",
  )

  /**
   * ```
   * non-indexable-root/
   * ├── non-indexable.txt
   * ├── indexable-one/
   * │   └── one.txt
   * └── indexable-two/
   *     └── two.txt
   * ```
   */
  @BeforeEach
  fun setUp(): Unit = runBlocking {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonIndexableKindFileSetTestContributor(), disposable)

    val nonIndexableRootPath = tempDir.newDirectoryPath("non-indexable-root")
    nonIndexableRootPath.resolve("non-indexable.txt").writeText("non-indexable")

    val indexableRootOnePath = nonIndexableRootPath.resolve("indexable-one").createDirectory()
    indexableRootOnePath.resolve("one.txt").writeText("one")

    val indexableRootTwoPath = nonIndexableRootPath.resolve("indexable-two").createDirectory()
    indexableRootTwoPath.resolve("two.txt").writeText("two")

    nonIndexableRoot = findVirtualFile(nonIndexableRootPath)
    indexableRootOne = findVirtualFile(indexableRootOnePath)
    indexableRootTwo = findVirtualFile(indexableRootTwoPath)

    workspaceModel.update { storage ->
      val module = ModuleEntity("module", emptyList(), NonPersistentEntitySource) {
        contentRoots = listOf(
          ContentRootEntity(indexableRootOne.toVirtualFileUrl(), emptyList(), NonPersistentEntitySource),
          ContentRootEntity(indexableRootTwo.toVirtualFileUrl(), emptyList(), NonPersistentEntitySource)
        )
      }
      storage.addEntity(module)
      storage.addEntity(NonIndexableTestEntity(nonIndexableRoot.toVirtualFileUrl(), NonPersistentEntitySource))
    }

    module = ModuleManager.getInstance(project).modules.single()

    readAction {
      assertThat(workspaceFileIndex.isIndexable(nonIndexableRoot)).isFalse()
      assertThat(workspaceFileIndex.isIndexable(indexableRootOne)).isTrue()
      assertThat(workspaceFileIndex.isIndexable(indexableRootTwo)).isTrue()
    }
  }

  @Test
  fun `ProjectIndexableFilesIteratorImpl unwraps cache avoiding files and skips non-indexable files`(): Unit = runBlocking {
    assertIteratesIndexableCacheableFiles(projectIndexableFilesIterator(), expectedIndexableFiles)
  }

  @Test
  fun `ModuleFilesIteratorImpl unwraps cache avoiding files and skips non-indexable files`(): Unit = runBlocking {
    assertIteratesIndexableCacheableFiles(moduleFilesIterator(), expectedIndexableFiles)
  }

  @Test
  fun `ModuleFilesIteratorImpl iterates non-recursive root only`(): Unit = runBlocking {
    assertIteratesIndexableCacheableFiles(moduleFilesIterator(indexableRootOne, recursive = false), listOf("indexable-one"))
  }

  private fun projectIndexableFilesIterator(): IndexableFilesIterator {
    return ProjectIndexableFilesIteratorImpl(nonIndexableRoot)
  }

  private fun moduleFilesIterator(root: VirtualFile = nonIndexableRoot, recursive: Boolean = true): IndexableFilesIterator {
    val constructor = Class.forName("com.intellij.util.indexing.roots.ModuleFilesIteratorImpl").getDeclaredConstructor(
      Module::class.java,
      VirtualFile::class.java,
      Boolean::class.javaPrimitiveType,
      Boolean::class.javaPrimitiveType,
    )
    return constructor.newInstance(module, root, recursive, true) as IndexableFilesIterator
  }

  private suspend fun assertIteratesIndexableCacheableFiles(iterator: IndexableFilesIterator, expectedRelativePaths: List<String>) {
    val files = collectFiles(iterator)

    assertThat(relativePaths(files)).containsExactlyInAnyOrderElementsOf(expectedRelativePaths)
    readAction {
      assertThat(files)
        .describedAs("cacheable and indexable files")
        .allMatch { file -> file !is CacheAvoidingVirtualFile && workspaceFileIndex.isIndexable(file) }
    }
  }

  private suspend fun collectFiles(iterator: IndexableFilesIterator): List<VirtualFile> {
    return readAction {
      val result = mutableListOf<VirtualFile>()
      val fullyProcessed = iterator.iterateFiles(
        project,
        ContentIterator { file ->
          result.add(file)
          true
        },
        VirtualFileFilter.ALL,
      )
      assertThat(fullyProcessed).isTrue()
      result
    }
  }

  private fun relativePaths(files: Collection<VirtualFile>): Set<String> {
    return files.mapTo(linkedSetOf()) { file ->
      VfsUtilCore.getRelativePath(file, nonIndexableRoot, '/') ?: file.path
    }
  }

  private fun findVirtualFile(path: Path): VirtualFile {
    return checkNotNull(com.intellij.testFramework.VfsTestUtil.findFileByCaseSensitivePath(path.pathString)) {
      "VirtualFile not found for path: $path"
    }
  }

  private fun VirtualFile.toVirtualFileUrl(): VirtualFileUrl = this.toVirtualFileUrl(vfuManager)
}
