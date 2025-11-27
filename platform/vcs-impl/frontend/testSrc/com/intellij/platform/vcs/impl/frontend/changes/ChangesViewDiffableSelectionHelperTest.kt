// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.LocalChangeListImpl
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.platform.vcs.impl.changes.ChangesViewTestBase
import com.intellij.platform.vcs.impl.shared.changes.ChangesTreePath
import com.intellij.platform.vcs.impl.shared.rpc.ContentRevisionDto
import com.intellij.platform.vcs.impl.shared.rpc.FilePathDto
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.tree.TreeUtil

internal class ChangesViewDiffableSelectionHelperTest : ChangesViewTestBase() {
  fun `test current change then next unversioned and no previous`() {
    val changePath = path("a.txt")
    val unversioned = path("u1.txt")

    val defaultList = defaultChangeList(changePath)

    val model = buildModel(view, listOf(defaultList), listOf(unversioned))
    updateModelAndSelect(view, model, changePath)

    val helper = ChangesViewDiffableSelectionHelper(view)
    runInEdtAndWait { helper.tryUpdateSelection() }
    val selection = helper.diffableSelection.value
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

    val helper = ChangesViewDiffableSelectionHelper(view)
    runInEdtAndWait { helper.tryUpdateSelection() }
    val selection = helper.diffableSelection.value
    checkNotNull(selection)
    assertTreePath(selection.selectedChange, p2, expectChangeId = true)
    assertTreePath(selection.previousChange!!, p1, expectChangeId = true)
    assertTreePath(selection.nextChange!!, p3, expectChangeId = true)
  }

  fun `test current unversioned then next unversioned and previous change`() {
    val c1 = path("c1.txt")
    val u1 = path("u1.txt")
    val u2 = path("u2.txt")

    val defaultList = defaultChangeList(c1)

    val model = buildModel(view, listOf(defaultList), listOf(u1, u2))
    updateModelAndSelect(view, model, u1)

    val helper = ChangesViewDiffableSelectionHelper(view)
    runInEdtAndWait { helper.tryUpdateSelection() }
    val selection = helper.diffableSelection.value
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

    val helper = ChangesViewDiffableSelectionHelper(view)
    runInEdtAndWait { helper.tryUpdateSelection() }
    val selection = helper.diffableSelection.value
    checkNotNull(selection)
    assertTreePath(selection.selectedChange, u1, expectChangeId = false)
    assertNull(selection.previousChange)
    assertTreePath(selection.nextChange!!, u2, expectChangeId = false)
  }

  fun `test only current change`() {
    val c1 = path("c1.txt")
    val defaultList = defaultChangeList(c1)

    val model = buildModel(view, listOf(defaultList), emptyList())
    updateModelAndSelect(view, model, c1)

    val helper = ChangesViewDiffableSelectionHelper(view)
    runInEdtAndWait { helper.tryUpdateSelection() }
    val selection = helper.diffableSelection.value
    checkNotNull(selection)
    assertTreePath(selection.selectedChange, c1, expectChangeId = true)
    assertNull(selection.previousChange)
    assertNull(selection.nextChange)
  }

  fun `test only current unversioned`() {
    val u1 = path("u1.txt")
    val model = buildModel(view, emptyList(), listOf(u1))
    updateModelAndSelect(view, model, u1)

    val helper = ChangesViewDiffableSelectionHelper(view)
    runInEdtAndWait { helper.tryUpdateSelection() }
    val selection = helper.diffableSelection.value
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
    runInEdtAndWait {
      val pathToList = view.findNodePathInTree(defaultList)
      assertNotNull(pathToList)
      view.clearSelection()
      view.addSelectionPath(pathToList)
      assertEquals(1, view.selectionCount)
      helper.tryUpdateSelection()
    }

    val updated = helper.diffableSelection.value
    assertSame(previous, updated)
  }

  fun `test unversioned selected then select unversioned root doesn't cause update`() {
    val u1 = path("u1.txt")
    val u2 = path("u2.txt")

    val model = buildModel(view, emptyList(), listOf(u1, u2))
    updateModelAndSelect(view, model, u1)

    val helper = ChangesViewDiffableSelectionHelper(view)
    runInEdtAndWait { helper.tryUpdateSelection() }
    val previous = helper.diffableSelection.value

    // Switch selection to Unversioned root node
    runInEdtAndWait {
      val unvNode = VcsTreeModelData.findTagNode(view, ChangesBrowserNode.UNVERSIONED_FILES_TAG)
      checkNotNull(unvNode)
      val pathToUnv = TreeUtil.getPathFromRoot(unvNode)
      view.clearSelection()
      view.addSelectionPath(pathToUnv)
      assertEquals(1, view.selectionCount)
      helper.tryUpdateSelection()
    }

    val updated = helper.diffableSelection.value
    assertSame(previous, updated)
  }

  fun `test switch from change to unversioned`() {
    val c1 = path("c1.txt")
    val u1 = path("u1.txt")

    val defaultList = defaultChangeList(c1)
    val model = buildModel(view, listOf(defaultList), listOf(u1))
    updateModelAndSelect(view, model, c1)

    val helper = ChangesViewDiffableSelectionHelper(view)
    runInEdtAndWait { helper.tryUpdateSelection() }
    val previous = helper.diffableSelection.value
    checkNotNull(previous)
    assertTreePath(previous.selectedChange, c1, true)

    runInEdtAndWait {
      val pathToU1 = view.findNodePathInTree(u1)
      assertNotNull(pathToU1)
      view.clearSelection()
      view.addSelectionPath(pathToU1)
      assertEquals(1, view.selectionCount)
      helper.tryUpdateSelection()
    }

    val selection = helper.diffableSelection.value
    checkNotNull(selection)
    assertTreePath(selection.selectedChange, u1, false)
    assertTreePath(selection.previousChange!!, c1, expectChangeId = true)
    assertNull(selection.nextChange)
  }

  private fun defaultChangeList(changePath: FilePath): LocalChangeListImpl = LocalChangeListImpl.Builder(project, "Default")
    .setDefault(true)
    .setChanges(listOf(change(changePath)))
    .build()

  private fun change(path: FilePath): Change {
    val contentRevisionDto = ContentRevisionDto("0", FilePathDto.toDto(path))
    return Change(null, contentRevisionDto.contentRevision)
  }

  private fun assertTreePath(path: ChangesTreePath, expected: FilePath, expectChangeId: Boolean) {
    assertEquals(expected, path.filePath.filePath)
    if (expectChangeId) assertNotNull(path.changeId) else assertNull(path.changeId)
  }
}
