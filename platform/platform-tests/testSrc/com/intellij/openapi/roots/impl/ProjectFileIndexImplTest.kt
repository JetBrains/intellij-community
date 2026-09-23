// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.indexing.testEntities.NonIndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.NonIndexableTestEntity
import com.intellij.util.indexing.testEntities.NonRecursiveFileSetContributor
import com.intellij.util.indexing.testEntities.NonRecursiveTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
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
  }

  /**
   * A non-recursive file set covers its own root only, so it must not suppress a recursive root nested under it.
   *
   * Regression test for RIDER-141737, and this is a Rider solution's layout: a recursive `CONTENT_NON_INDEXABLE` root on
   * the project directory, a non-recursive `CONTENT` root on an ancestor. The whole project used to disappear.
   */
  @Test
  fun `iterateContent visits a recursive root nested under a non-recursive content root`(): Unit = runBlocking {
    val outerDir = tempDir.newVirtualDirectory("outer")
    val projectDir = tempDir.newVirtualDirectory("outer/project")
    val assetsDir = tempDir.newVirtualDirectory("outer/project/Assets")
    val asset = tempDir.newVirtualFile("outer/project/Assets/HintArrow.prefab")
    registerNonRecursiveContentRoot(outerDir)
    registerRecursiveNonIndexableContentRoot(projectDir)

    val iterated = iterateContent()

    assertThat(iterated).containsExactlyInAnyOrder(outerDir.path, projectDir.path, assetsDir.path, asset.path)
  }

  private suspend fun registerNonRecursiveContentRoot(root: VirtualFile) {
    workspaceModel.update { storage ->
      storage.addEntity(NonRecursiveTestEntity(root.toVirtualFileUrl(virtualFileUrlManager), NonPersistentEntitySource))
    }
  }

  private suspend fun registerRecursiveNonIndexableContentRoot(root: VirtualFile) {
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(root.toVirtualFileUrl(virtualFileUrlManager), NonPersistentEntitySource))
    }
  }

  /** Paths, not files: cache-avoiding root wrappers do not compare by identity. */
  private fun iterateContent(): List<String> {
    val paths = mutableListOf<String>()
    ProjectFileIndex.getInstance(projectModel.project).iterateContent(ContentIterator { file ->
      paths.add(file.path)
      true
    })
    return paths
  }
}
