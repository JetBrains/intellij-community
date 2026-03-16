// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.LocalChangeListImpl
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.platform.vcs.impl.changes.ChangesViewTestBase
import com.intellij.platform.vcs.impl.shared.changes.ChangesTreePath
import com.intellij.platform.vcs.impl.shared.rpc.ChangeId
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewDiffableSelection
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.tree.TreeUtil

internal class ChangesViewDiffableSelectionHelperTest : ChangesViewTestBase() {
  fun `test current change then next unversioned and no previous`() {
    val changePath = path("a.txt")
    val unversioned = path("u1.txt")

    val defaultList = defaultChangeList(project, changePath)

    val model = buildModel(view, listOf(defaultList), listOf(unversioned))
    updateModelAndSelect(view, model, changePath)

    val selection = ChangesViewDiffableSelectionHelper(view).updateSelection()
    checkNotNull(selection)
    assertTreePath(selection.selectedChange, changePath, expectChangeId = true)
    assertNull(selection.previousChange)
    assertTreePath(selection.nextChange!!, unversioned, expectChangeId = false)
  }

  fun `test current change with previous and next changes`() {
    val p1 = path("c1.txt")
    val p2 = path("c2.txt")
    val p3 = path("c3.txt")

    val defaultList = LocalChangeListImpl.Builder(project, "Default").setDefault(true)
      .setChanges(listOf(change(p1), change(p2), change(p3)))
      .build()

    val model = buildModel(view, listOf(defaultList), emptyList())
    updateModelAndSelect(view, model, p2)

    val selection = ChangesViewDiffableSelectionHelper(view).updateSelection()
    checkNotNull(selection)
    assertTreePath(selection.selectedChange, p2, expectChangeId = true)
    assertTreePath(selection.previousChange!!, p1, expectChangeId = true)
    assertTreePath(selection.nextChange!!, p3, expectChangeId = true)
  }

  fun `test current unversioned then next unversioned and previous change`() {
    val c1 = path("c1.txt")
    val u1 = path("u1.txt")
    val u2 = path("u2.txt")

    val defaultList = defaultChangeList(project, c1)

    val model = buildModel(view, listOf(defaultList), listOf(u1, u2))
    updateModelAndSelect(view, model, u1)

    val selection = ChangesViewDiffableSelectionHelper(view).updateSelection()
    checkNotNull(selection)
    assertTreePath(selection.selectedChange, u1, expectChangeId = false)
    assertTreePath(selection.previousChange!!, c1, expectChangeId = true)
    assertTreePath(selection.nextChange!!, u2, expectChangeId = false)
  }

  fun `test only unversioned files present`() {
    val u1 = path("u1.txt")
    val u2 = path("u2.txt")
    val u3 = path("u3.txt")

    val model = buildModel(view, emptyList(), listOf(u1, u2, u3))
    updateModelAndSelect(view, model, u1)

    val selection = ChangesViewDiffableSelectionHelper(view).updateSelection()
    checkNotNull(selection)
    assertTreePath(selection.selectedChange, u1, expectChangeId = false)
    assertNull(selection.previousChange)
    assertTreePath(selection.nextChange!!, u2, expectChangeId = false)
  }

  fun `test only current change`() {
    val c1 = path("c1.txt")
    val defaultList = defaultChangeList(project, c1)

    val model = buildModel(view, listOf(defaultList), emptyList())
    updateModelAndSelect(view, model, c1)

    val selection = ChangesViewDiffableSelectionHelper(view).updateSelection()
    checkNotNull(selection)
    assertTreePath(selection.selectedChange, c1, expectChangeId = true)
    assertNull(selection.previousChange)
    assertNull(selection.nextChange)
  }

  fun `test only current unversioned`() {
    val u1 = path("u1.txt")
    val model = buildModel(view, emptyList(), listOf(u1))
    updateModelAndSelect(view, model, u1)

    val selection = ChangesViewDiffableSelectionHelper(view).updateSelection()
    checkNotNull(selection)
    assertTreePath(selection.selectedChange, u1, expectChangeId = false)
    assertNull(selection.previousChange)
    assertNull(selection.nextChange)
  }

  fun `test change selected then selection removed`() {
    val c1 = path("c1.txt")
    val c2 = path("c2.txt")

    val defaultList = LocalChangeListImpl.Builder(project, "Default").setDefault(true)
      .setChanges(listOf(change(c1), change(c2)))
      .build()

    val model = buildModel(view, listOf(defaultList), emptyList())
    updateModelAndSelect(view, model, c1)

    val helper = ChangesViewDiffableSelectionHelper(view)
    runInEdtAndWait { helper.tryUpdateSelection() }
    assertNotNull(helper.diffableSelection.value)

    runInEdtAndWait {
      view.clearSelection()
      helper.tryUpdateSelection()
    }
    assertNull(helper.diffableSelection.value)
  }


  fun `test change selected then select changelist node doesn't cause update`() {
    val c1 = path("c1.txt")
    val c2 = path("c2.txt")

    val defaultList = LocalChangeListImpl.Builder(project, "Default").setDefault(true)
      .setChanges(listOf(change(c1), change(c2)))
      .build()

    val model = buildModel(view, listOf(defaultList), emptyList())
    updateModelAndSelect(view, model, c1)

    val helper = ChangesViewDiffableSelectionHelper(view)
    runInEdtAndWait { helper.tryUpdateSelection() }
    val previous = helper.diffableSelection.value

    // Switch selection to the changelist node
    findNodeAndSelect(defaultList)
    val updated = helper.updateSelection()
    assertSame(previous, updated)
  }

  fun `test unversioned selected then select unversioned root doesn't cause update`() {
    val u1 = path("u1.txt")
    val u2 = path("u2.txt")

    val model = buildModel(view, emptyList(), listOf(u1, u2))
    updateModelAndSelect(view, model, u1)

    val helper = ChangesViewDiffableSelectionHelper(view)
    val previous = helper.updateSelection()

    // Switch selection to Unversioned root node
    findNodeAndSelect(ChangesBrowserNode.UNVERSIONED_FILES_TAG)

    val updated = helper.updateSelection()
    assertSame(previous, updated)
  }

  fun `test switch from change to unversioned`() {
    val c1 = path("c1.txt")
    val u1 = path("u1.txt")

    val defaultList = defaultChangeList(project, c1)
    val model = buildModel(view, listOf(defaultList), listOf(u1))
    updateModelAndSelect(view, model, c1)

    val helper = ChangesViewDiffableSelectionHelper(view)
    val previous = helper.updateSelection()
    checkNotNull(previous)
    assertTreePath(previous.selectedChange, c1, true)

    findNodeAndSelect(u1)
    val selection = helper.updateSelection()
    checkNotNull(selection)
    assertTreePath(selection.selectedChange, u1, false)
    assertTreePath(selection.previousChange!!, c1, expectChangeId = true)
    assertNull(selection.nextChange)
  }

  fun `test change node and amend node affecting same file both selectable`() {
    val filePath = path("a.txt")

    val defaultList = defaultChangeList(project, filePath)

    val changeInAmend = change(filePath, "amend-revision")
    val editedCommit = createEditedCommit(listOf(changeInAmend))

    val model = buildModel(view, listOf(defaultList), emptyList(), editedCommit)
    updateModelAndSelect(view, model, filePath)

    val selection = ChangesViewDiffableSelectionHelper(view).updateSelection()
    checkNotNull(selection)

    assertTreePath(selection.selectedChange, defaultList.changes.single())
    assertTreePath(selection.nextChange!!, changeInAmend)
    assertNull(selection.previousChange)
  }

  fun `test multiple changes under amend node all navigable`() {
    val pathInTheMiddle = path("c2.txt")

    val c1 = change(path("c1.txt"))
    val c2 = change(path("c2.txt"))
    val c3 = change(path("c3.txt"))
    val editedCommit = createEditedCommit(listOf(c1, c2, c3))

    val model = buildModel(view, emptyList(), emptyList(), editedCommit)
    updateModelAndSelect(view, model, pathInTheMiddle)

    val selection = ChangesViewDiffableSelectionHelper(view).updateSelection()
    checkNotNull(selection)

    assertTreePath(selection.selectedChange, c2)
    assertTreePath(selection.previousChange!!, c1)
    assertTreePath(selection.nextChange!!, c3)
  }

  fun `test switch from change node to amend node affecting same file`() {
    val filePath = path("a.txt")

    val defaultList = defaultChangeList(project, filePath)

    val changeInAmend = change(filePath, "amend-revision")
    val model = buildModel(view, listOf(defaultList), emptyList(), createEditedCommit(listOf(changeInAmend)))
    updateModelAndSelect(view, model, filePath)

    val helper = ChangesViewDiffableSelectionHelper(view)
    val previous = helper.updateSelection()
    checkNotNull(previous)
    assertTreePath(previous.selectedChange, defaultList.changes.single())

    // Switch selection to the amend node for the same file
    findNodeAndSelect(changeInAmend)

    val selection = helper.updateSelection()
    checkNotNull(selection)
    assertTreePath(selection.selectedChange, changeInAmend)
    assertTreePath(selection.previousChange!!, defaultList.changes.single())
    assertNull(selection.nextChange)
  }

  private fun ChangesViewDiffableSelectionHelper.updateSelection(): ChangesViewDiffableSelection? {
    runInEdtAndWait { tryUpdateSelection() }
    return diffableSelection.value
  }

  private fun findNodeAndSelect(node: Any) {
    runInEdtAndWait {
      val amendNodePath = view.root.traverse().find { it.userObject === node }
      requireNotNull(amendNodePath) { "Node $node not found" }
      view.clearSelection()
      view.addSelectionPath(TreeUtil.getPathFromRoot(amendNodePath))
      assertEquals(1, view.selectionCount)
    }
  }

  private fun assertTreePath(path: ChangesTreePath, expected: FilePath, expectChangeId: Boolean) {
    assertEquals(expected, path.filePath.filePath)
    if (expectChangeId) assertNotNull(path.changeId) else assertNull(path.changeId)
  }

  private fun assertTreePath(path: ChangesTreePath, expected: Change) {
    assertEquals(ChangeId.getId(expected), path.changeId)
  }
}
