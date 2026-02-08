// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.internal.visitChildrenInVfsRecursively
import com.intellij.openapi.Disposable
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.testFramework.VfsTestUtil
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.pathString
import kotlin.io.path.writeText

@TestApplication
class NonIndexableFilesNotLoadedIntoVfsFileIndexTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @JvmField
  @RegisterExtension
  val tempDir: TempDirectoryExtension = TempDirectoryExtension()

  @TestDisposable
  private lateinit var disposable: Disposable

  private val project get() = projectModel.project
  private val workspaceModel get() = project.workspaceModel
  private val urlManager get() = workspaceModel.getVirtualFileUrlManager()
  private val projectFileIndex get() = ProjectFileIndex.getInstance(project)

  @BeforeEach
  fun setUp() {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonIndexableKindFileSetTestContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(IndexableKindFileSetTestContributor(), disposable)
  }

  private fun collectFilesInVfs(root: VirtualFile): Set<String> {
    return visitChildrenInVfsRecursively(root).map { it.name }.toSet()
  }

  private fun iterateContentUnderDirectory(root: VirtualFile): Set<String> {
    val result = mutableSetOf<String>()
    projectFileIndex.iterateContentUnderDirectory(root) { file ->
      result.add(file.name)
      true
    }
    return result
  }

  private fun findVirtualFile(path: Path): VirtualFile {
    return checkNotNull(VfsTestUtil.findFileByCaseSensitivePath(path.pathString)) {
      "VirtualFile not found for path: $path"
    }
  }

  private suspend fun registerRoots(
    nonIndexable: List<VirtualFile> = emptyList(),
    indexable: List<VirtualFile> = emptyList(),
    excluded: List<VirtualFile> = emptyList(),
  ) {
    workspaceModel.update { storage ->
      for (root in nonIndexable) {
        storage.addEntity(NonIndexableTestEntity(root.toVirtualFileUrl(urlManager), NonPersistentEntitySource))
      }
      if (indexable.isNotEmpty() || excluded.isNotEmpty()) {
        storage.addEntity(IndexingTestEntity(indexable.map { it.toVirtualFileUrl(urlManager) },
                                             excluded.map { it.toVirtualFileUrl(urlManager) },
                                             NonPersistentEntitySource))
      }
    }
  }

  @Test
  fun `non-indexable files not loaded into VFS after iterateContentUnderDirectory`(): Unit = runBlocking {
    // Directory structure: non-indexable/{file1.txt, file2.txt, subdir/file3.txt}
    val rootDir = tempDir.newDirectoryPath("non-indexable")
    rootDir.resolve("file1.txt").writeText("content1")
    rootDir.resolve("file2.txt").writeText("content2")
    rootDir.resolve("subdir").createDirectory()
    rootDir.resolve("subdir/file3.txt").writeText("content3")

    val rootVirtualFile = findVirtualFile(rootDir)
    registerRoots(nonIndexable = listOf(rootVirtualFile))

    assertThat(collectFilesInVfs(rootVirtualFile))
      .describedAs("Before iteration, only root should be in VFS")
      .containsExactlyInAnyOrder("non-indexable")

    val iteratedFiles = iterateContentUnderDirectory(rootVirtualFile)
    assertThat(iteratedFiles).containsExactlyInAnyOrder("non-indexable", "file1.txt", "file2.txt", "subdir", "file3.txt")

    val filesInVfs = collectFilesInVfs(rootVirtualFile)
    assertThat(filesInVfs).describedAs("Non-indexable child files should NOT be loaded into VFS")
      .doesNotContain("file1.txt", "file2.txt", "subdir", "file3.txt")
  }

  @Test
  fun `indexable files ARE loaded into VFS after iterateContentUnderDirectory`(): Unit = runBlocking {
    // Directory structure: indexable/{file1.txt, file2.txt}
    val rootDir = tempDir.newDirectoryPath("indexable")
    rootDir.resolve("file1.txt").writeText("content1")
    rootDir.resolve("file2.txt").writeText("content2")

    val root = findVirtualFile(rootDir)
    registerRoots(indexable = listOf(root))

    assertThat(collectFilesInVfs(root))
      .describedAs("Before iteration, only root should be in VFS")
      .containsExactlyInAnyOrder("indexable")

    val iteratedFiles = iterateContentUnderDirectory(root)
    assertThat(iteratedFiles).containsExactlyInAnyOrder("indexable", "file1.txt", "file2.txt")

    val filesInVfs = collectFilesInVfs(root)
    assertThat(filesInVfs).describedAs("Indexable root and files should be loaded into VFS")
      .containsExactlyInAnyOrder("indexable", "file1.txt", "file2.txt")
  }

  @Test
  fun `nested non-indexable under indexable - all files loaded into VFS`(): Unit = runBlocking {
    // Directory structure: indexable-root/{indexable-file.txt, non-indexable-subdir/file-under-non-indexable.txt}
    val rootDir = tempDir.newDirectoryPath("indexable-root")
    rootDir.resolve("indexable-file.txt").writeText("content")
    val nonIndexableDir = rootDir.resolve("non-indexable-subdir").createDirectory()
    nonIndexableDir.resolve("file-under-non-indexable.txt").writeText("content")

    val root = findVirtualFile(rootDir)
    val nonIndexableRoot = findVirtualFile(nonIndexableDir)
    registerRoots(nonIndexable = listOf(nonIndexableRoot), indexable = listOf(root))

    assertThat(collectFilesInVfs(root))
      .describedAs("Before iteration, only roots passed to `registerRoots` should be in VFS")
      .containsExactlyInAnyOrder("indexable-root", "non-indexable-subdir")

    val iteratedFiles = iterateContentUnderDirectory(root)
    assertThat(iteratedFiles).containsExactlyInAnyOrder("indexable-root",
                                                        "indexable-file.txt",
                                                        "non-indexable-subdir",
                                                        "file-under-non-indexable.txt")

    val filesInVfs = collectFilesInVfs(root)
    assertThat(filesInVfs).describedAs("All files under indexable root should be loaded into VFS")
      .containsExactlyInAnyOrder("indexable-root", "indexable-file.txt", "non-indexable-subdir", "file-under-non-indexable.txt")
  }

  @Test
  fun `non-indexable under exclude iterated and not loaded into vfs`(): Unit = runBlocking {
    // Directory structure: u1/{file-in-u1.txt, exclude/u2/file-in-u2.txt}
    val u1Dir = tempDir.newDirectoryPath("u1")
    val excludeDir = u1Dir.resolve("exclude").createDirectory()
    val u2Dir = excludeDir.resolve("u2").createDirectory()
    u2Dir.resolve("file-in-u2.txt").writeText("content")
    u1Dir.resolve("file-in-u1.txt").writeText("content")

    val u1 = findVirtualFile(u1Dir)
    val exclude = findVirtualFile(excludeDir)
    val u2 = findVirtualFile(u2Dir)
    registerRoots(nonIndexable = listOf(u1, u2), excluded = listOf(exclude))

    assertThat(collectFilesInVfs(u1))
      .describedAs("Before iteration, only roots passed to `registerRoots` should be in VFS")
      .containsExactlyInAnyOrder("u1", "exclude", "u2")

    val iteratedFiles = iterateContentUnderDirectory(u1)
    assertThat(iteratedFiles).containsExactlyInAnyOrder("u1", "file-in-u1.txt", "u2", "file-in-u2.txt")

    val filesInVfs = collectFilesInVfs(u1)
    assertThat(filesInVfs).describedAs("Non-indexable child files should NOT be loaded into VFS")
      .doesNotContain("file-in-u1.txt", "file-in-u2.txt")
  }
}
