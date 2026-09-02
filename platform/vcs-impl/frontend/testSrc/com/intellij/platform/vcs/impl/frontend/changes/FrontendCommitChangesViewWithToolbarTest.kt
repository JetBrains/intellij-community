// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.vcs.changes.LocalChangeListImpl
import com.intellij.openapi.vcs.changes.LocalChangesListView
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.platform.vcs.impl.changes.ChangesViewTestBase
import com.intellij.platform.vcs.impl.shared.changes.ChangesTreePath
import com.intellij.platform.vcs.impl.shared.rpc.ChangeId
import com.intellij.platform.vcs.impl.shared.rpc.FilePathDto
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.swing.tree.DefaultTreeModel

internal class FrontendCommitChangesViewWithToolbarTest : ChangesViewTestBase() {
  fun `test edited commit change gets selected when regular change with same path exists`() {
    val file = path("same.txt")
    val list = defaultChangeList(project, file)

    val changeInAmend = change(file, revision = "amend-revision")

    val model = buildModel(view, listOf(list), emptyList(), createEditedCommit(listOf(changeInAmend)))
    updateModel(view, model)

    testPanel { panel ->
      panel.selectPath(ChangesTreePath(FilePathDto.toDto(file), ChangeId.getId(changeInAmend)))
      assertSelection(changeInAmend)
    }
  }

  fun `test switch from change to unversioned`() {
    val changePath = path("c1.txt")
    val unversioned = path("u1.txt")

    val list = defaultChangeList(project, changePath)

    val model = buildModel(view, listOf(list), listOf(unversioned))
    updateModel(view, model)

    val change = list.changes.single()

    testPanel { panel ->
      // First select the change node
      panel.selectPath(ChangesTreePath(FilePathDto.toDto(changePath), ChangeId.getId(change)))
      assertSelection(change)

      // Then switch selection to the unversioned file node
      panel.selectPath(ChangesTreePath(FilePathDto.toDto(unversioned), changeId = null))
      assertSelection(unversioned)
    }
  }

  fun `test navigating inside a multiple selection keeps it`() {
    val c1 = change(path("c1.txt"))
    val c2 = change(path("c2.txt"))
    val c3 = change(path("c3.txt"))

    val list = LocalChangeListImpl.Builder(project, "Default").setDefault(true)
      .setChanges(listOf(c1, c2, c3))
      .build()

    val model = buildModel(view, listOf(list), emptyList())
    updateModel(view, model)

    testPanel { panel ->
      selectNodes(c1, c2)

      // "Compare Next File" inside the selection
      panel.selectPath(checkNotNull(ChangesTreePath.create(c2)))

      // Only the previewed file moved, the selection is untouched
      assertEquals(2, view.selectionCount)
    }
  }

  fun `test navigating outside a multiple selection moves the tree selection`() {
    val c1 = change(path("c1.txt"))
    val c2 = change(path("c2.txt"))
    val c3 = change(path("c3.txt"))

    val list = LocalChangeListImpl.Builder(project, "Default").setDefault(true)
      .setChanges(listOf(c1, c2, c3))
      .build()

    val model = buildModel(view, listOf(list), emptyList())
    updateModel(view, model)

    testPanel { panel ->
      selectNodes(c1, c2)

      panel.selectPath(checkNotNull(ChangesTreePath.create(c3)))

      assertSelection(c3)
    }
  }

  private fun selectNodes(vararg nodes: Any) {
    val treePaths = nodes.map { node ->
      val treeNode = requireNotNull(view.root.traverse().find { it.userObject === node }) { "Node $node not found" }
      TreeUtil.getPathFromRoot(treeNode)
    }
    view.clearSelection()
    treePaths.forEach { view.addSelectionPath(it) }
    assertEquals(nodes.size, view.selectionCount)
  }

  private fun assertSelection(expected: Any) {
    assertEquals(1, view.selectionCount)
    assertSame(expected, TreeUtil.getLastUserObject(view.selectionPath))
  }

  private fun updateModel(view: LocalChangesListView, model: DefaultTreeModel) = runInEdtAndWait {
    view.updateTreeModel(model, ChangesTree.ALWAYS_RESET)
    view.clearSelection()
  }

  private fun testPanel(block: (FrontendCommitChangesViewWithToolbarPanel) -> Unit) {
    val testCs = CoroutineScope(SupervisorJob())
    try {
      val panel = FrontendCommitChangesViewWithToolbarPanel(view, testCs, ChangesViewDelegatingInclusionModel(project, testCs))
      runInEdtAndWait {
        block(panel)
      }
    }
    finally {
      testCs.cancel()
    }
  }
}
