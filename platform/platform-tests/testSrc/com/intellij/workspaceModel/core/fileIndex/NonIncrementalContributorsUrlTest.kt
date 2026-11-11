// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.workspaceModel.core.fileIndex.impl.TraverseState
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileSets
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.function.Executable

@TestApplication
internal class NonIncrementalContributorsUrlTest {
  @RegisterExtension
  @Order(-1)
  private val libraryDir = TempDirectoryExtension()

  @RegisterExtension
  @Order(0)
  private val createRootsBeforeProject = BeforeEachCallback { context ->
    val directory = projectModel.baseProjectDir
    directory.before(context.displayName)
    timeoutRunBlocking {
      edtWriteAction {
        for (path in listOf("library/src", "library/bin", "library/excluded", "excluded")) {
          directory.newVirtualDirectory(path)
        }
        libraryDir.newVirtualDirectory("src")
      }
    }
  }

  @RegisterExtension
  @Order(1)
  private val projectModel = ProjectModelExtension()

  @Test
  fun `traversal refreshes synthetic library roots before URL lookup`(): Unit = timeoutRunBlocking {
    edtWriteAction {
      val root = libraryDir.virtualFileRoot.findChild("src")!!
      val library = SyntheticLibrary.newImmutableLibrary(listOf(root))
      val provider = object : AdditionalLibraryRootsProvider() {
        override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> = listOf(library)
      }
      AdditionalLibraryRootsProvider.EP_NAME.point.registerExtension(provider, projectModel.disposableRule.disposable)
      WorkspaceFileIndexEx.getInstance(projectModel.project).indexData.resetCustomContributors()
      assertUrlsAbsent(listOf(root))

      val state = TraverseState.getTraverseState(root, projectModel.project)
      assertNotNull(state)
      assertTrue(state!!.isIndexable)
      assertUrlRegistered(root)
    }
  }

  @Test
  fun `synthetic library roots have registered URLs`(): Unit = timeoutRunBlocking {
    val roots = edtWriteAction {
      val sourceRoot = projectModel.baseProjectDir.virtualFileRoot.findFileByRelativePath("library/src")!!
      val binaryRoot = projectModel.baseProjectDir.virtualFileRoot.findFileByRelativePath("library/bin")!!
      val excludedRoot = projectModel.baseProjectDir.virtualFileRoot.findFileByRelativePath("library/excluded")!!
      val roots = listOf(sourceRoot, binaryRoot, excludedRoot)
      assertUrlsAbsent(roots)
      val library = SyntheticLibrary.newImmutableLibrary(listOf(sourceRoot), listOf(binaryRoot), setOf(excludedRoot), null)
      val provider = object : AdditionalLibraryRootsProvider() {
        override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> = listOf(library)
      }
      AdditionalLibraryRootsProvider.EP_NAME.point.registerExtension(provider, projectModel.disposableRule.disposable)
      WorkspaceFileIndexEx.getInstance(projectModel.project).indexData.resetCustomContributors()
      roots
    }
    readAction {
      assertAll(roots.map { root -> Executable { assertUrlRegistered(root) } })
    }
  }

  @Test
  fun `existing roots from an exclude policy have registered URLs`(): Unit = timeoutRunBlocking {
    val root = edtWriteAction {
      val root = projectModel.baseProjectDir.virtualFileRoot.findFileByRelativePath("excluded")!!
      assertUrlsAbsent(listOf(root))
      val policy = object : DirectoryIndexExcludePolicy {
        override fun getExcludeUrlsForProject(): Array<String> = arrayOf(root.url)
      }
      DirectoryIndexExcludePolicy.EP_NAME.getPoint(projectModel.project).registerExtension(policy, projectModel.disposableRule.disposable)
      WorkspaceFileIndexEx.getInstance(projectModel.project).indexData.resetCustomContributors()
      root
    }
    readAction {
      assertUrlRegistered(root)
    }
  }

  private fun assertUrlsAbsent(roots: List<VirtualFile>) {
    val urlManager = projectModel.project.workspaceModel.getVirtualFileUrlManager()
    roots.forEach { assertNull(urlManager.get(it.url), "The URL must be absent before registration: ${it.url}") }
  }

  private fun assertUrlRegistered(root: VirtualFile) {
    val index = WorkspaceFileIndexEx.getInstance(projectModel.project)
    val fileSets = index.indexData.getFileSetsAt(root)
    assertNotEquals(WorkspaceFileSets.EMPTY, fileSets)
    val url = projectModel.project.workspaceModel.getVirtualFileUrlManager().get(root.url)
    assertNotNull(url, "The file sets have no registered URL: ${root.url}")
    assertEquals(fileSets, index.getFileSetsAt(url!!))
  }
}
