// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.projectView.impl.ProjectViewUpdateRequestsService
import com.intellij.platform.projectView.impl.project.ProjectPaneModel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference

private const val ROOTS_NODE = "Node Roots"

/**
 * The frontend user object of a node gives the files that the node stands for (IJPL-258221).
 *
 * A node without a single file gives its [ProjectViewNode.getRoots], for example the directories of a package.
 */
@TestApplication
internal class ProjectViewPaneNodeFilesTest : AbstractProjectViewPaneTest() {
  private val project = projectFixture(pathFixture = tempPathFixture(subdirName = "pvNodeFiles"))
  private val srcRoot = project.moduleFixture(name = "pvNodeFiles").sourceRootFixture(pathFixture = tempPathFixture(subdirName = "src"))
  private val disposable = disposableFixture()

  @Test
  fun `a node without a file gives its roots and a directory node gives its directory`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    val (first, second) = createDirectories("first", "second")
    registerRootsNode { listOf(first, second) }
    withProjectViewPane(project.get(), ProjectPaneModel.ID) { pane ->
      pane.awaitTree { ROOTS_NODE in it }
      waitUntilAssertSucceeds("The nodes should give their files", PANE_TEST_WAIT_TIMEOUT) {
        assertEquals(listOf(first, second), pane.virtualFilesOf(ROOTS_NODE))
        assertEquals(listOf(first), pane.virtualFilesOf("first"))
      }
    }
  }

  @Test
  fun `an update of a node gives its new roots`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    val (first, second) = createDirectories("first", "second")
    val roots = AtomicReference(listOf(first))
    registerRootsNode { roots.get() }
    withProjectViewPane(project.get(), ProjectPaneModel.ID) { pane ->
      pane.awaitTree { ROOTS_NODE in it }
      waitUntilAssertSucceeds("The node should give its first roots", PANE_TEST_WAIT_TIMEOUT) {
        assertEquals(listOf(first), pane.virtualFilesOf(ROOTS_NODE))
      }

      // An update request for a root updates only this node, so the pane keeps its user object.
      roots.set(listOf(first, second))
      ProjectViewUpdateRequestsService.getInstance(project.get()).requestUpdate(first, deep = false)

      waitUntilAssertSucceeds("The node should give its new roots", PANE_TEST_WAIT_TIMEOUT) {
        assertEquals(listOf(first, second), pane.virtualFilesOf(ROOTS_NODE))
      }
    }
  }

  private suspend fun createDirectories(vararg names: String): List<VirtualFile> {
    val src = srcRoot.get().virtualFile
    return edtWriteAction {
      names.map { src.createChildDirectory(this@ProjectViewPaneNodeFilesTest, it) }
    }
  }

  private fun registerRootsNode(roots: () -> List<VirtualFile>) {
    TreeStructureProvider.EP.getPoint(project.get()).registerExtension(RootsTreeStructureProvider(roots), disposable.get())
  }
}

private fun ProjectViewPaneTester.virtualFilesOf(mainText: String): List<VirtualFile>? {
  return findLoadedNode(mainText)?.projectViewNode?.userObject?.getVirtualFiles()
}

/** Adds a [RootsNode] under the project node. */
private class RootsTreeStructureProvider(private val roots: () -> List<VirtualFile>) : TreeStructureProvider {
  override fun modify(
    parent: AbstractTreeNode<*>,
    children: Collection<AbstractTreeNode<*>>,
    settings: ViewSettings,
  ): Collection<AbstractTreeNode<*>> {
    if (parent !is ProjectViewProjectNode) return children
    return children + RootsNode(parent.project, roots, settings)
  }
}

/** A node that has no single file but stands for its [roots], as a package node does. */
private class RootsNode(
  project: Project,
  private val roots: () -> List<VirtualFile>,
  settings: ViewSettings,
) : ProjectViewNode<String>(project, ROOTS_NODE, settings) {
  override fun getRoots(): Collection<VirtualFile> = roots()

  override fun contains(file: VirtualFile): Boolean = roots().any { VfsUtilCore.isAncestor(it, file, false) }

  override fun canRepresent(element: Any?): Boolean = element in roots() || super.canRepresent(element)

  override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

  override fun update(presentation: PresentationData) {
    presentation.presentableText = ROOTS_NODE
  }
}
