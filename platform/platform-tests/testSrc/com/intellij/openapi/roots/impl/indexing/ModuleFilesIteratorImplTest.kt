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
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.testEntities.NonIndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.NonIndexableTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
internal class ModuleFilesIteratorImplTest {
  @RegisterExtension
  private val projectModel = ProjectModelExtension()

  @RegisterExtension
  private val tempDir = TempDirectoryExtension()


  private val disposable: Disposable get() = projectModel.disposableRule.disposable

  private val project get() = projectModel.project
  private val workspaceModel get() = project.workspaceModel
  private val virtualFileUrlManager get() = workspaceModel.getVirtualFileUrlManager()

  private lateinit var module: Module
  private lateinit var moduleRoot: VirtualFile
  private lateinit var innerRoot: VirtualFile

  @BeforeEach
  fun setUp(): Unit = runBlocking {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonIndexableKindFileSetTestContributor(), disposable)

    moduleRoot = tempDir.newVirtualDirectory("root")
    tempDir.newVirtualFile("root/root.txt")
    innerRoot = tempDir.newVirtualDirectory("root/inner")
    tempDir.newVirtualFile("root/inner/inner.txt")

    workspaceModel.update { storage ->
      storage.addEntity(ModuleEntity("module", emptyList(), NonPersistentEntitySource) {
        contentRoots = listOf(ContentRootEntity(moduleRoot.toVirtualFileUrl(), emptyList(), NonPersistentEntitySource))
      })
    }

    IndexingTestUtil.waitUntilIndexesAreReady(project)

    module = ModuleManager.getInstance(project).modules.single()
  }

  @Test
  fun `iterates module root without an inner non-indexable fileset`(): Unit = runBlocking {
    assertIteratesModuleRoot()
  }

  @Test
  fun `iterates module root with an inner non-indexable fileset`(): Unit = runBlocking {
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(innerRoot.toVirtualFileUrl(), NonPersistentEntitySource))
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)

    assertIteratesModuleRoot()
  }

  private suspend fun assertIteratesModuleRoot() {
    val files = readAction {
      val result = mutableListOf<VirtualFile>()
      val fullyProcessed = moduleFilesIterator().iterateFiles(
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

    assertThat(relativePaths(files)).containsExactlyInAnyOrder("", "root.txt", "inner", "inner/inner.txt")
  }

  private fun moduleFilesIterator(): IndexableFilesIterator =
    IndexableEntityProviderMethods.createModuleContentIterators(module, moduleRoot, true).single()

  private fun relativePaths(files: Collection<VirtualFile>): Set<String> {
    return files.mapTo(linkedSetOf()) { file ->
      VfsUtilCore.getRelativePath(file, moduleRoot, '/') ?: ""
    }
  }

  private fun VirtualFile.toVirtualFileUrl(): VirtualFileUrl = this.toVirtualFileUrl(virtualFileUrlManager)
}
