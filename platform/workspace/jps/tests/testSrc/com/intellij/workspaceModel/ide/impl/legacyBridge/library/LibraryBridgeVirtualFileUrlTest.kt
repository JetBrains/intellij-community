// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * A [LibraryBridgeImpl] gets its [VirtualFileUrlManager] from its `origin`: a project library uses the manager of the
 * project, and an application library uses the global manager. This test holds that behavior.
 *
 * A manager makes one instance for each URL, and two instances from different managers are never equal.
 * Therefore, a bridge must use the manager of the storage that keeps its entity.
 * `SdkBridgeImpl` does not do this, and it must always use the global manager. Do not make libraries do the same.
 */
@TestApplication
class LibraryBridgeVirtualFileUrlTest {

  @RegisterExtension
  @JvmField
  val projectModel = ProjectModelExtension()

  @Test
  fun `a project library makes its urls in the manager of the project`() {
    val projectManager = projectVirtualFileUrlManager()
    val globalManager = globalVirtualFileUrlManager()
    assertNotSame(projectManager, globalManager, "this test needs two different managers")

    projectModel.addProjectLevelLibrary(LIBRARY_NAME) { it.addRoot(CLASSES_URL, OrderRootType.CLASSES) }

    val root = projectLibraryRoot(LibraryTableId.ProjectLibraryTableId)
    assertSame(
      projectManager.getOrCreateFromUrl(CLASSES_URL), root.url,
      "the URL of a project library root must be the instance from the manager of the project",
    )
    assertNotSame(
      globalManager.getOrCreateFromUrl(CLASSES_URL), root.url,
      "the URL of a project library root must not come from the global manager",
    )
  }

  @Test
  fun `an application library makes its urls in the global manager`() {
    val projectManager = projectVirtualFileUrlManager()
    val globalManager = globalVirtualFileUrlManager()
    assertNotSame(projectManager, globalManager, "this test needs two different managers")

    projectModel.addApplicationLevelLibrary(LIBRARY_NAME) { it.addRoot(CLASSES_URL, OrderRootType.CLASSES) }

    val tableId = LibraryTableId.GlobalLibraryTableId(LibraryTablesRegistrar.APPLICATION_LEVEL)
    val globalSnapshot = GlobalWorkspaceModel.getInstance(LocalEelMachine).currentSnapshot
    val root = libraryRoot(globalSnapshot.resolve(LibraryId(LIBRARY_NAME, tableId))?.roots)
    assertSame(
      globalManager.getOrCreateFromUrl(CLASSES_URL), root.url,
      "the URL of an application library root must be the instance from the global manager",
    )

    // GlobalWorkspaceModel copies global libraries into each project, and it makes the URLs again in the manager of
    // the project. Therefore the copy must not keep the instance of the global manager.
    val copiedRoot = libraryRoot(
      WorkspaceModel.getInstance(projectModel.project).currentSnapshot.resolve(LibraryId(LIBRARY_NAME, tableId))?.roots
    )
    assertSame(
      projectManager.getOrCreateFromUrl(CLASSES_URL), copiedRoot.url,
      "the copy in the project must use the manager of the project",
    )
  }

  private fun projectVirtualFileUrlManager(): VirtualFileUrlManager =
    WorkspaceModel.getInstance(projectModel.project).getVirtualFileUrlManager()

  private fun globalVirtualFileUrlManager(): VirtualFileUrlManager =
    GlobalWorkspaceModel.getInstance(LocalEelMachine).getVirtualFileUrlManager()

  private fun projectLibraryRoot(tableId: LibraryTableId): LibraryRoot {
    val snapshot = WorkspaceModel.getInstance(projectModel.project).currentSnapshot
    return libraryRoot(snapshot.resolve(LibraryId(LIBRARY_NAME, tableId))?.roots)
  }

  private fun libraryRoot(roots: List<LibraryRoot>?): LibraryRoot {
    checkNotNull(roots) { "the test library '$LIBRARY_NAME' is not in the storage" }
    return roots.single()
  }

  private companion object {
    const val LIBRARY_NAME = "test-library"
    const val CLASSES_URL = "jar:///library/classes.jar!/"
  }
}
