// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesViewTreeStateStrategy
import com.intellij.openapi.vcs.changes.LocalChangeListImpl
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.platform.vcs.changes.ChangesUtil
import com.intellij.platform.vcs.impl.changes.ChangesViewTestBase
import com.intellij.platform.vcs.impl.shared.changes.ChangesTreePath
import com.intellij.platform.vcs.impl.shared.rpc.ChangeId
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewDiffableSelection
import com.intellij.testFramework.runInEdtAndGet
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
    // The counter is scoped to the changelist of the selected change, so the unversioned file, which is the next change
    // to navigate to, is not counted.
    assertCounter(selection, selectedIndex = 0, changesCount = 1)
  }

  fun `test counter counts all changes when scoping to group is disabled`() {
    Registry.get("vcs.diff.preview.scope.navigation.to.group").setValue(false, testRootDisposable)

    val changePath = path("a.txt")
    val unversioned = path("u1.txt")

    val defaultList = defaultChangeList(project, changePath)

    val model = buildModel(view, listOf(defaultList), listOf(unversioned))
    updateModelAndSelect(view, model, changePath)

    val selection = ChangesViewDiffableSelectionHelper(view).updateSelection()
    checkNotNull(selection)
    assertTreePath(selection.selectedChange, changePath, expectChangeId = true)
    // Without scoping to the group the unversioned file is counted too.
    assertCounter(selection, selectedIndex = 0, changesCount = 2)
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
    assertCounter(selection, selectedIndex = 1, changesCount = 3)
  }

  fun `test counter is scoped to the changelist of the selected change`() {
    val a1 = path("a1.txt")
    val a2 = path("a2.txt")
    val b1 = path("b1.txt")
    val b2 = path("b2.txt")
    val b3 = path("b3.txt")

    val listA = LocalChangeListImpl.Builder(project, "A").setDefault(true)
      .setChanges(listOf(change(a1), change(a2)))
      .build()
    val listB = LocalChangeListImpl.Builder(project, "B")
      .setChanges(listOf(change(b1), change(b2), change(b3)))
      .build()

    val model = buildModel(view, listOf(listA, listB), emptyList())
    updateModelAndSelect(view, model, b2)

    val selection = ChangesViewDiffableSelectionHelper(view).updateSelection()
    checkNotNull(selection)
    assertTreePath(selection.selectedChange, b2, expectChangeId = true)
    // Only the changes of "B" are counted, not the ones of "A" above it.
    assertCounter(selection, selectedIndex = 1, changesCount = 3)
  }

  fun `test counter falls back to the selection when there is no changelist node`() {
    val p1 = path("c1.txt")
    val p2 = path("c2.txt")
    val p3 = path("c3.txt")

    val defaultList = LocalChangeListImpl.Builder(project, "Default").setDefault(true)
      .setChanges(listOf(change(p1), change(p2), change(p3)))
      .build()

    // This is what the Changes View does when changelist support is disabled.
    val model = buildModel(view, listOf(defaultList), emptyList(), skipSingleDefaultChangeList = true)
    updateModelAndSelect(view, model, p2)

    val selection = ChangesViewDiffableSelectionHelper(view).updateSelection()
    checkNotNull(selection)
    assertTreePath(selection.selectedChange, p2, expectChangeId = true)
    // Without a changelist node there is no group, so the counter is scoped to the selection, as in monolith mode.
    assertCounter(selection, selectedIndex = 0, changesCount = 1)
    // Navigation is not affected.
    assertTreePath(selection.previousChange!!, p1, expectChangeId = true)
    assertTreePath(selection.nextChange!!, p3, expectChangeId = true)
  }

  fun `test counter is scoped to the explicit multiple selection`() {
    val paths = (1..5).map { path("c$it.txt") }
    val changes = paths.map { change(it) }

    val defaultList = LocalChangeListImpl.Builder(project, "Default").setDefault(true)
      .setChanges(changes)
      .build()

    val model = buildModel(view, listOf(defaultList), emptyList())
    updateModelAndSelect(view, model, paths[1])
    findNodesAndSelect(changes[1], changes[3])

    val selection = ChangesViewDiffableSelectionHelper(view).updateSelection()
    checkNotNull(selection)
    // The previewed change must not switch
    assertTreePath(selection.selectedChange, changes[1])
    // "1 of 2": only the two selected files are counted, not the whole changelist.
    assertCounter(selection, selectedIndex = 0, changesCount = 2)
  }

  fun `test navigation is scoped to the explicit multiple selection`() {
    val paths = (1..5).map { path("c$it.txt") }
    val changes = paths.map { change(it) }

    val defaultList = LocalChangeListImpl.Builder(project, "Default").setDefault(true)
      .setChanges(changes)
      .build()

    val model = buildModel(view, listOf(defaultList), emptyList())
    updateModelAndSelect(view, model, paths[1])
    findNodesAndSelect(changes[1], changes[3])

    val selection = ChangesViewDiffableSelectionHelper(view).updateSelection()
    checkNotNull(selection)
    assertTreePath(selection.selectedChange, changes[1])
    // changes[0] and changes[2] are skipped: navigation walks the selected files only, as in monolith mode.
    assertNull(selection.previousChange)
    assertTreePath(selection.nextChange!!, changes[3])
  }

  fun `test moving inside the explicit multiple selection keeps it and reports the new position`() {
    val paths = (1..5).map { path("c$it.txt") }
    val changes = paths.map { change(it) }

    val defaultList = LocalChangeListImpl.Builder(project, "Default").setDefault(true)
      .setChanges(changes)
      .build()

    val model = buildModel(view, listOf(defaultList), emptyList())
    updateModelAndSelect(view, model, paths[1])
    findNodesAndSelect(changes[1], changes[3])

    val helper = ChangesViewDiffableSelectionHelper(view)
    val before = helper.updateSelection()
    checkNotNull(before)
    assertCounter(before, selectedIndex = 0, changesCount = 2)

    // "Compare Next File" inside the selection
    val moved = helper.moveWithinSelection(before.nextChange!!)
    assertTrue("The move must be handled without touching the tree selection", moved)

    val after = helper.diffableSelection.value
    checkNotNull(after)
    assertTreePath(after.selectedChange, changes[3])
    // "2 of 2", and the selection is still both files
    assertCounter(after, selectedIndex = 1, changesCount = 2)
    assertTreePath(after.previousChange!!, changes[1])
    assertNull(after.nextChange)
  }

  fun `test moving outside the explicit multiple selection is left to the tree`() {
    val paths = (1..3).map { path("c$it.txt") }
    val changes = paths.map { change(it) }

    val defaultList = LocalChangeListImpl.Builder(project, "Default").setDefault(true)
      .setChanges(changes)
      .build()

    val model = buildModel(view, listOf(defaultList), emptyList())
    updateModelAndSelect(view, model, paths[0])
    findNodesAndSelect(changes[0], changes[1])

    val helper = ChangesViewDiffableSelectionHelper(view)
    checkNotNull(helper.updateSelection())

    val path = checkNotNull(ChangesTreePath.create(changes[2]))
    assertFalse(helper.moveWithinSelection(path))
  }

  fun `test moving with a single selection is left to the tree`() {
    val p1 = path("c1.txt")
    val p2 = path("c2.txt")

    val defaultList = LocalChangeListImpl.Builder(project, "Default").setDefault(true)
      .setChanges(listOf(change(p1), change(p2)))
      .build()

    val model = buildModel(view, listOf(defaultList), emptyList())
    updateModelAndSelect(view, model, p1)

    val helper = ChangesViewDiffableSelectionHelper(view)
    val selection = checkNotNull(helper.updateSelection())

    assertFalse(helper.moveWithinSelection(selection.nextChange!!))
  }

  fun `test counter for a selection spanning several changelists is scoped to the selection`() {
    val b1 = path("b1.txt")
    val changeA1 = change(path("a1.txt"))
    val changeB1 = change(b1)
    val changeB2 = change(path("b2.txt"))

    val listA = LocalChangeListImpl.Builder(project, "A").setDefault(true)
      .setChanges(listOf(changeA1))
      .build()
    val listB = LocalChangeListImpl.Builder(project, "B")
      .setChanges(listOf(changeB1, changeB2))
      .build()

    val model = buildModel(view, listOf(listA, listB), emptyList())
    updateModelAndSelect(view, model, b1)

    val helper = ChangesViewDiffableSelectionHelper(view)
    val single = helper.updateSelection()
    checkNotNull(single)
    // A single selection is scoped to its changelist, which holds two changes.
    assertCounter(single, selectedIndex = 0, changesCount = 2)

    // Ctrl-click a change from another changelist
    findNodesAndSelect(changeA1, changeB1)

    val extended = helper.updateSelection()
    checkNotNull(extended)
    assertTreePath(extended.selectedChange, changeB1)
    // The explicit selection wins over the changelist: "2 of 2", in the tree display order.
    assertCounter(extended, selectedIndex = 1, changesCount = 2)
  }

  fun `test counter is reported for a huge selection`() {
    val changesNumberExceedingLimit = ChangesUtil.MANY_CHANGES_THRESHOLD + 2
    val paths = (0..<changesNumberExceedingLimit).map { path("c$it.txt") }

    val defaultList = LocalChangeListImpl.Builder(project, "Default").setDefault(true)
      .setChanges(paths.map { change(it) })
      .build()

    val model = buildModel(view, listOf(defaultList), emptyList())
    updateModelAndSelect(view, model, paths.first())
    findNodesAndSelect(defaultList)

    val selection = ChangesViewDiffableSelectionHelper(view).updateSelection()
    checkNotNull(selection)
    assertCounter(selection, selectedIndex = 0, changesCount = changesNumberExceedingLimit)
  }

  fun `test counter is updated after model change with the same selected change`() {
    val p1 = path("c1.txt")
    val p2 = path("c2.txt")
    val p3 = path("c3.txt")
    val c1 = change(p1)
    val c2 = change(p2)
    val c3 = change(p3)

    val defaultList = LocalChangeListImpl.Builder(project, "Default").setDefault(true)
      .setChanges(listOf(c1, c2))
      .build()
    updateModelAndSelect(view, buildModel(view, listOf(defaultList), emptyList()), p1)

    val helper = ChangesViewDiffableSelectionHelper(view)
    val before = helper.updateSelection()
    checkNotNull(before)
    assertCounter(before, selectedIndex = 0, changesCount = 2)

    val updatedList = LocalChangeListImpl.Builder(project, "Default").setDefault(true)
      .setChanges(listOf(c1, c2, c3))
      .build()
    runInEdtAndWait {
      view.updateTreeModel(buildModel(view, listOf(updatedList), emptyList()), ChangesViewTreeStateStrategy())
      helper.updateSelectionAfterModelChange()
    }

    val after = helper.diffableSelection.value
    checkNotNull(after)
    assertTreePath(after.selectedChange, p1, expectChangeId = true)
    assertCounter(after, selectedIndex = 0, changesCount = 3)
  }

  fun `test ignored files are neither counted nor navigable`() {
    Registry.get("vcs.diff.preview.scope.navigation.to.group").setValue(false, testRootDisposable)

    val changePath = path("a.txt")
    val defaultList = defaultChangeList(project, changePath)

    val model = buildModel(view, listOf(defaultList), emptyList(), ignored = listOf(path("i1.txt"), path("i2.txt")))
    updateModelAndSelect(view, model, changePath)

    val selection = ChangesViewDiffableSelectionHelper(view).updateSelection()
    checkNotNull(selection)
    // Even with scoping to the group disabled the ignored files are not diffable, so they are not counted.
    assertCounter(selection, selectedIndex = 0, changesCount = 1)
    assertNull(selection.nextChange)
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
    // For an unversioned selection the counter is scoped to the unversioned files.
    assertCounter(selection, selectedIndex = 0, changesCount = 2)
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
    assertCounter(selection, selectedIndex = 0, changesCount = 3)
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
    assertCounter(selection, selectedIndex = 0, changesCount = 1)
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


  fun `test extending the selection doesn't switch the previewed change`() {
    val paths = (1..3).map { path("c$it.txt") }
    val changes = paths.map { change(it) }

    val defaultList = LocalChangeListImpl.Builder(project, "Default").setDefault(true)
      .setChanges(changes)
      .build()

    val model = buildModel(view, listOf(defaultList), emptyList())
    updateModelAndSelect(view, model, paths[0])

    val helper = ChangesViewDiffableSelectionHelper(view)
    val before = helper.updateSelection()
    checkNotNull(before)
    assertTreePath(before.selectedChange, changes[0])

    // Ctrl-click a second file: the first one stays selected, so the update must not be skipped
    findNodesAndSelect(changes[0], changes[1])

    val after = helper.updateSelection()
    checkNotNull(after)
    // The previewed change must not switch
    assertTreePath(after.selectedChange, changes[0])
  }

  fun `test shrinking the selection doesn't switch the previewed change`() {
    val paths = (1..3).map { path("c$it.txt") }
    val changes = paths.map { change(it) }

    val defaultList = LocalChangeListImpl.Builder(project, "Default").setDefault(true)
      .setChanges(changes)
      .build()

    val model = buildModel(view, listOf(defaultList), emptyList())
    updateModelAndSelect(view, model, paths[0])
    findNodesAndSelect(changes[0], changes[1])

    val helper = ChangesViewDiffableSelectionHelper(view)
    checkNotNull(helper.updateSelection())

    // Back to a single selection
    findNodesAndSelect(changes[0])

    val after = helper.updateSelection()
    checkNotNull(after)
    assertTreePath(after.selectedChange, changes[0])
  }

  fun `test change selected then select changelist node doesn't switch the selected change`() {
    val c1 = path("c1.txt")
    val c2 = path("c2.txt")

    val defaultList = LocalChangeListImpl.Builder(project, "Default").setDefault(true)
      .setChanges(listOf(change(c1), change(c2)))
      .build()

    val model = buildModel(view, listOf(defaultList), emptyList())
    updateModelAndSelect(view, model, c1)

    val helper = ChangesViewDiffableSelectionHelper(view)
    helper.updateSelection()
    val previous = helper.diffableSelection.value
    checkNotNull(previous)

    // Switch selection to the changelist node
    findNodeAndSelect(defaultList)
    val updated = helper.updateSelection()
    checkNotNull(updated)

    // The changelist node selects all its changes, but the change shown in the diff preview must not switch.
    assertEquals(previous.selectedChange, updated.selectedChange)
    assertEquals(previous.previousChange, updated.previousChange)
    assertEquals(previous.nextChange, updated.nextChange)
  }

  fun `test unversioned selected then select unversioned root doesn't switch the selected change`() {
    val u1 = path("u1.txt")
    val u2 = path("u2.txt")

    val model = buildModel(view, emptyList(), listOf(u1, u2))
    updateModelAndSelect(view, model, u1)

    val helper = ChangesViewDiffableSelectionHelper(view)
    val previous = helper.updateSelection()
    checkNotNull(previous)

    // Switch selection to Unversioned root node
    findNodeAndSelect(ChangesBrowserNode.UNVERSIONED_FILES_TAG)

    val updated = helper.updateSelection()
    checkNotNull(updated)
    assertEquals(previous.selectedChange, updated.selectedChange)
    assertEquals(previous.previousChange, updated.previousChange)
    assertEquals(previous.nextChange, updated.nextChange)
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
    // The amend commit node is not a group, so the counter is scoped to the selection and shows "1 file" as in monolith mode.
    // All three changes stay navigable, only the counter is narrowed.
    assertCounter(selection, selectedIndex = 0, changesCount = 1)
  }

  fun `test counter counts all selected changes under amend node`() {
    val c1 = change(path("c1.txt"))
    val c2 = change(path("c2.txt"))
    val c3 = change(path("c3.txt"))
    val editedCommit = createEditedCommit(listOf(c1, c2, c3))

    val model = buildModel(view, emptyList(), emptyList(), editedCommit)
    updateModelAndSelect(view, model, path("c2.txt"))

    val helper = ChangesViewDiffableSelectionHelper(view)
    val single = helper.updateSelection()
    checkNotNull(single)
    // The amend commit node is not a group, so the counter is scoped to the selection, which is a single file for now.
    assertCounter(single, selectedIndex = 0, changesCount = 1)

    // Ctrl-click the file above the selected one: the previewed change stays, but the counter now covers both files
    findNodesAndSelect(c1, c2)

    val extended = helper.updateSelection()
    checkNotNull(extended)
    assertTreePath(extended.selectedChange, c2)
    // "2 of 2": the previewed change is the second one within the selection.
    assertCounter(extended, selectedIndex = 1, changesCount = 2)

    // Ctrl-click the third file too
    findNodesAndSelect(c1, c2, c3)

    val extendedFurther = helper.updateSelection()
    checkNotNull(extendedFurther)
    assertTreePath(extendedFurther.selectedChange, c2)
    assertCounter(extendedFurther, selectedIndex = 1, changesCount = 3)
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
    // The amend commit node is not a group, so the counter is scoped to the selection, as in monolith mode.
    assertCounter(selection, selectedIndex = 0, changesCount = 1)
  }

  private fun ChangesViewDiffableSelectionHelper.updateSelection(): ChangesViewDiffableSelection? {
    runInEdtAndWait { tryUpdateSelection() }
    return diffableSelection.value
  }

  private fun ChangesViewDiffableSelectionHelper.moveWithinSelection(path: ChangesTreePath): Boolean =
    runInEdtAndGet { moveWithinExplicitSelection(path) }

  private fun findNodeAndSelect(node: Any) {
    findNodesAndSelect(node)
  }

  private fun findNodesAndSelect(vararg nodes: Any) {
    runInEdtAndWait {
      val treePaths = nodes.map { node ->
        val treeNode = view.root.traverse().find { it.userObject === node }
        requireNotNull(treeNode) { "Node $node not found" }
        TreeUtil.getPathFromRoot(treeNode)
      }
      view.clearSelection()
      treePaths.forEach { view.addSelectionPath(it) }
      assertEquals(nodes.size, view.selectionCount)
    }
  }

  private fun assertCounter(selection: ChangesViewDiffableSelection, selectedIndex: Int, changesCount: Int) {
    assertEquals("selectedIndex", selectedIndex, selection.selectedIndex)
    assertEquals("changesCount", changesCount, selection.changesCount)
  }

  private fun assertTreePath(path: ChangesTreePath, expected: FilePath, expectChangeId: Boolean) {
    assertEquals(expected, path.filePath.filePath)
    if (expectChangeId) assertNotNull(path.changeId) else assertNull(path.changeId)
  }

  private fun assertTreePath(path: ChangesTreePath, expected: Change) {
    assertEquals(ChangeId.getId(expected), path.changeId)
  }
}
