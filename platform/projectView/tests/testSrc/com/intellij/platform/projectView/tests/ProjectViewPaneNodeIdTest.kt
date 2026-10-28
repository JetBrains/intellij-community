// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.projectView.impl.project.ProjectPaneModel
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test

/**
 * A node keeps its ID when the children update gets a new user object for the same node.
 *
 * Then the frontend updates the node, and does not remove it and add it again.
 * So the node keeps its selection and its expanded state, and the tree does not flicker.
 *
 * Each test starts a full update of the tree, as a plugin load does.
 * The same children update adds a marker node next to the file under test.
 * So when the marker node shows, the PV has also updated the node of the file under test.
 * A file change cannot be the marker: its update reads the old nodes, and that refreshes their PSI pointers.
 */
@TestApplication
internal class ProjectViewPaneNodeIdTest : AbstractProjectViewPaneTest() {
  companion object {
    private const val BLUEPRINT = "platform/projectView/tests/testData/paneTreeExample"
  }

  // Each test gets its own project, because each test changes the files.
  private val project = projectFixture(pathFixture = tempPathFixture(subdirName = "pvNodeId"))
  private val srcRoot = project.moduleFixture(name = "pvNodeId").sourceRootFixture(
    isTestSource = false,
    pathFixture = tempPathFixture(subdirName = "src"),
    blueprintResourcePath = projectViewTestDataPath(ProjectViewPaneNodeIdTest::class.java, BLUEPRINT),
  )
  private val disposable = disposableFixture()

  private val markers = MarkerTreeStructureProvider()

  @Test
  fun `a file node keeps its ID when the PSI of the file is created again`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    val src = srcRoot.get().virtualFile
    val hello = src.findChild("Hello.txt")!!
    registerMarkers()
    withProjectViewPane(project.get(), ProjectPaneModel.ID) { pane ->
      val idBefore = pane.expand("pvNodeId", "src", "Hello.txt").id
      // Keep the old PsiFile reachable: the old node caches it through a soft reference, and a cleared reference hides the bug.
      val psiBefore = findPsiFile(hello)

      // A plugin load makes this call, see PsiVfsInitProjectActivity.
      // It drops all the view providers, so the next access creates a new PsiFile.
      edtWriteAction { PsiManagerEx.getInstanceEx(project.get()).fileManagerEx.processFileTypesChanged(true) }
      assertNotSame(psiBefore, findPsiFile(hello), "The PSI reset must create a new PsiFile")

      updateAllAndWaitForMarker(pane, src, "pvNodeId", "src")
      assertSubtreeWithoutMarker(
        pane, listOf("pvNodeId", "src"),
        """
        src
         sub
          World.txt
         Hello.txt
        """
      )
      assertEquals(idBefore, pane.expand("pvNodeId", "src", "Hello.txt").id, "The node of Hello.txt must keep its ID")
    }
  }

  @Test
  fun `a file node keeps its ID when the file is renamed`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    val sub = srcRoot.get().virtualFile.findChild("sub")!!
    val world = sub.findChild("World.txt")!!
    registerMarkers()
    withProjectViewPane(project.get(), ProjectPaneModel.ID) { pane ->
      val idBefore = pane.expand("pvNodeId", "src", "sub", "World.txt").id

      edtWriteAction { world.rename(this@ProjectViewPaneNodeIdTest, "Renamed.txt") }

      updateAllAndWaitForMarker(pane, sub, "pvNodeId", "src", "sub")
      assertSubtreeWithoutMarker(
        pane, listOf("pvNodeId", "src", "sub"),
        """
        sub
         Renamed.txt
        """
      )
      assertEquals(idBefore, pane.expand("pvNodeId", "src", "sub", "Renamed.txt").id, "The node of the renamed file must keep its ID")
    }
  }

  private fun registerMarkers() {
    TreeStructureProvider.EP.getPoint(project.get()).registerExtension(markers, disposable.get())
  }

  private suspend fun findPsiFile(file: VirtualFile): PsiFile? {
    return readAction { PsiManager.getInstance(project.get()).findFile(file) }
  }

  private suspend fun updateAllAndWaitForMarker(pane: ProjectViewPaneTester, dir: VirtualFile, vararg pathToDir: String) {
    markers.markerParent = dir
    // The PV updates the whole tree on this event. See TreeStructureProjectViewUpdater.
    edtWriteAction {
      AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(project.get(), null, emptyList(), emptyList(), "ProjectViewPaneNodeIdTest")
    }
    waitUntil("$MARKER should have appeared under '${pathToDir.last()}'", PANE_TEST_WAIT_TIMEOUT) {
      MARKER in pane.dumpSubtree(*pathToDir).lines().map { it.trim() }
    }
  }

  private suspend fun assertSubtreeWithoutMarker(pane: ProjectViewPaneTester, pathByMainText: List<String>, expected: String) {
    val actual = pane.dumpSubtree(*pathByMainText.toTypedArray()).lines().filterNot { it.trim() == MARKER }
    assertEquals(expected.trimIndent().trimEnd(), actual.joinToString("\n"))
  }
}

private const val MARKER = "Marker"

/** Adds a [MARKER] node to the children of the directory [markerParent], when it is set. */
private class MarkerTreeStructureProvider : TreeStructureProvider {
  @Volatile
  var markerParent: VirtualFile? = null

  override fun modify(
    parent: AbstractTreeNode<*>,
    children: Collection<AbstractTreeNode<*>>,
    settings: ViewSettings,
  ): Collection<AbstractTreeNode<*>> {
    val markerParent = markerParent ?: return children
    if (parent !is PsiDirectoryNode || parent.virtualFile != markerParent) return children
    return children + MarkerNode(parent.project, settings)
  }
}

private class MarkerNode(project: Project, settings: ViewSettings) : ProjectViewNode<String>(project, MARKER, settings) {
  override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

  override fun contains(file: VirtualFile): Boolean = false

  override fun isAlwaysLeaf(): Boolean = true

  override fun update(presentation: PresentationData) {
    presentation.presentableText = MARKER
  }
}
