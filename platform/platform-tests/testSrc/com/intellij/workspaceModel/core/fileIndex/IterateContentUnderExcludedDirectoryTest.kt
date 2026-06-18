// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIteratorEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.util.containers.TreeNodeProcessingResult
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
internal class IterateContentUnderExcludedDirectoryTest {
  @RegisterExtension
  private val projectExtension = ProjectModelExtension()
  private val project: Project get() = projectExtension.project

  @Test
  fun `content fileset under exclude by-pattern`(): Unit = runBlocking {
    // Use TempFileSystem directly to avoid VFS refresh/events: local history listens to them and may call
    // processContentUnderDirectory before this test checks that excludedByPattern has no cached VirtualFileUrl.
    val testRoot = createTempDirectory(TempFileSystem.getInstance().findFileByPath("/")!!,
                                       "iterateContentUnderExcludedDirectory-${System.nanoTime()}")
    val contentRoot = createTempDirectory(testRoot, "content")
    val contentFile = createTempFile(contentRoot, "contentFile.txt")
    val excludedByPattern = createTempDirectory(contentRoot, "excludedByPattern")
    val contentUnderExclude = createTempDirectory(excludedByPattern, "some/other/dirs/between/contentUnderExclude")
    val innerContentFile = createTempFile(contentUnderExclude, "innerContentFile.txt")

    val contentRootVfu = contentRoot.toVirtualFileUrl()
    val contentRootUnderExcludeVfu = contentUnderExclude.toVirtualFileUrl()
    project.workspaceModel.update("create content roots") { storage ->
      val moduleEntity = ModuleEntity("module", emptyList(), NonPersistentEntitySource) {
        contentRoots = listOf(ContentRootEntity(contentRootVfu, listOf(excludedByPattern.name), NonPersistentEntitySource),
                              ContentRootEntity(contentRootUnderExcludeVfu, emptyList(), NonPersistentEntitySource))
      }
      val m = storage.addEntity(moduleEntity)
    }
    assertThat(project.workspaceModel.getVirtualFileUrlManager().findByUrl(excludedByPattern.url)).isNull()

    val files = mutableSetOf<VirtualFile>()
    val processor = ContentIteratorEx { file ->
      files.add(file)
      TreeNodeProcessingResult.CONTINUE
    }

    WorkspaceFileIndexEx.getInstance(project)
      .processContentUnderDirectory(contentRoot, processor, VirtualFileFilter.ALL) { true }

    assertThat(files).containsExactlyInAnyOrder(contentRoot, contentFile, contentUnderExclude, innerContentFile)
  }

  private fun VirtualFile.toVirtualFileUrl(): VirtualFileUrl =
    this.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())

  private fun createTempDirectory(parent: VirtualFile, relativePath: String): VirtualFile {
    val fileSystem = TempFileSystem.getInstance()
    return relativePath.split('/').fold(parent) { currentParent, name ->
      fileSystem.createChildDirectory(this, currentParent, name)
      fileSystem.findFileByPath(childPath(currentParent, name))!!
    }
  }

  private fun createTempFile(parent: VirtualFile, name: String): VirtualFile {
    val fileSystem = TempFileSystem.getInstance()
    fileSystem.createChildFile(this, parent, name)
    return fileSystem.findFileByPath(childPath(parent, name))!!
  }

  private fun childPath(parent: VirtualFile, childName: String): String =
    if (parent.path == "/") "/$childName" else "${parent.path}/$childName"
}
