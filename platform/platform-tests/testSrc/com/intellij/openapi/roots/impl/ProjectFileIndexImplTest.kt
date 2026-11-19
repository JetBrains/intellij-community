// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.platform.backend.workspace.storeAndGet
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.indexing.testEntities.IndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.IndexingTestEntity
import com.intellij.util.indexing.testEntities.NonIndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.NonIndexableTestEntity
import com.intellij.util.indexing.testEntities.NonRecursiveFileSetContributor
import com.intellij.util.indexing.testEntities.NonRecursiveTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
internal class ProjectFileIndexImplTest {
  @RegisterExtension
  private val projectModel = ProjectModelExtension()

  @RegisterExtension
  private val tempDir = TempDirectoryExtension()

  private val disposable: Disposable get() = projectModel.disposableRule.disposable
  private val workspaceModel get() = projectModel.project.workspaceModel
  private val virtualFileUrlManager get() = workspaceModel.getVirtualFileUrlManager()

  @BeforeEach
  fun setUp() {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonIndexableKindFileSetTestContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonRecursiveFileSetContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(IndexableKindFileSetTestContributor(), disposable)
  }

  /**
   * A non-recursive file set covers its own root only, so it must not suppress a recursive root nested under it.
   *
   * Regression test for RIDER-141737, and this is a Rider solution's layout: a recursive `CONTENT_NON_INDEXABLE` root on
   * the project directory, a non-recursive `CONTENT` root on an ancestor. The whole project used to disappear.
   */
  @Test
  fun `iterateContent visits a recursive root nested under a non-recursive content root`(): Unit = timeoutRunBlocking {
    val outerDir = tempDir.newVirtualDirectory("outer")
    val projectDir = tempDir.newVirtualDirectory("outer/project")
    val assetsDir = tempDir.newVirtualDirectory("outer/project/Assets")
    val asset = tempDir.newVirtualFile("outer/project/Assets/HintArrow.prefab")
    registerNonRecursiveContentRoot(outerDir)
    registerRecursiveNonIndexableContentRoot(projectDir)

    val iterated = iterateContent()

    assertThat(iterated).containsExactlyInAnyOrder(outerDir.path, projectDir.path, assetsDir.path, asset.path)
  }

  /**
   * A walk without a custom filter takes no read action per file.
   * The only read actions are the ones of the roots step.
   */
  @Test
  fun `iterateContent without a filter takes no read action per file`(): Unit = timeoutRunBlocking {
    assertThat(readActionsOfWalk(filter = null)).isLessThanOrEqualTo(MAX_ROOTS_STEP_READ_ACTIONS)
  }

  /** [VirtualFileFilter.ALL] needs no read lock, so a walk with it takes no read action per file. */
  @Test
  fun `iterateContent with the ALL filter takes no read action per file`(): Unit = timeoutRunBlocking {
    assertThat(readActionsOfWalk(VirtualFileFilter.ALL)).isLessThanOrEqualTo(MAX_ROOTS_STEP_READ_ACTIONS)
  }

  private suspend fun readActionsOfWalk(filter: VirtualFileFilter?): Int {
    val root = tempDir.newVirtualDirectory("content")
    for (dirIndex in 0 until 3) {
      tempDir.newVirtualDirectory("content/d$dirIndex")
      for (fileIndex in 0 until 10) {
        tempDir.newVirtualFile("content/d$dirIndex/f$fileIndex.txt")
      }
    }
    registerRecursiveContentRoot(root)

    val (_, readActions) = countReadActions {
      ProjectFileIndex.getInstance(projectModel.project).iterateContent(ContentIterator { true }, filter)
    }
    return readActions
  }

  /** A walk without a custom filter visits each file once. */
  @Test
  fun `iterateContent without a filter visits a non-recursive root inside a recursive root once`(): Unit = timeoutRunBlocking {
    val outerDir = tempDir.newVirtualDirectory("outer")
    val innerDir = tempDir.newVirtualDirectory("outer/inner")
    registerRecursiveContentRoot(outerDir)
    registerNonRecursiveContentRoot(innerDir)

    val iterated = iterateContent()

    assertThat(iterated.filter { it == innerDir.path }).hasSize(1)
  }

  /** A custom filter can need the read lock, so the walk still runs it under a read action. */
  @Test
  fun `iterateContent with a filter runs the filter in a read action`(): Unit = timeoutRunBlocking {
    val root = tempDir.newVirtualDirectory("content")
    val file = tempDir.newVirtualFile("content/a.txt")
    registerRecursiveContentRoot(root)

    val filtered = mutableListOf<String>()
    val iterated = iterateContent(VirtualFileFilter { candidate ->
      assertThat(ApplicationManager.getApplication().isReadAccessAllowed).isTrue()
      filtered.add(candidate.path)
      true
    })

    assertThat(filtered).contains(file.path)
    assertThat(iterated).contains(file.path)
  }

  private suspend fun registerRecursiveContentRoot(root: VirtualFile) {
    workspaceModel.update { storage ->
      storage.addEntity(
        IndexingTestEntity(listOf(virtualFileUrlManager.storeAndGet(root)), emptyList(), NonPersistentEntitySource)
      )
    }
  }

  private suspend fun registerNonRecursiveContentRoot(root: VirtualFile) {
    workspaceModel.update { storage ->
      storage.addEntity(NonRecursiveTestEntity(virtualFileUrlManager.storeAndGet(root), NonPersistentEntitySource))
    }
  }

  private suspend fun registerRecursiveNonIndexableContentRoot(root: VirtualFile) {
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(virtualFileUrlManager.storeAndGet(root), NonPersistentEntitySource))
    }
  }

  /** Paths, not files: cache-avoiding root wrappers do not compare by identity. */
  private fun iterateContent(filter: VirtualFileFilter? = null): List<String> {
    val paths = mutableListOf<String>()
    ProjectFileIndex.getInstance(projectModel.project).iterateContent(ContentIterator { file ->
      paths.add(file.path)
      true
    }, filter)
    return paths
  }
}

/** The read actions of the roots step, with a margin. A walk with one read action per file takes more than 30. */
private const val MAX_ROOTS_STEP_READ_ACTIONS = 3
