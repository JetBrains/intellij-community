// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vcs.changes.*
import com.intellij.platform.vcs.changes.ChangesUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.runInEdtAndWait
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

internal class LocalChangesListViewSelectFileTest : LightPlatformTestCase() {
  fun `test selects unversioned file path`() {
    val view = LocalChangesListView(project)

    val unversionedPath = fakePath("unversioned.txt")
    val defaultList = LocalChangeListImpl.Builder(project, "Default")
      .setDefault(true)
      .setChanges(listOf(Change(null, CurrentContentRevision.create(fakePath("default-change.txt")))))
      .build()

    val model = buildModel(view, listOf(defaultList), listOf(unversionedPath))
    updateModelAndSelect(view, model, unversionedPath)

    assertEquals(unversionedPath, view.selectedUnversionedFiles.single())
  }

  fun `test selects change by file path`() {
    val view = LocalChangesListView(project)

    val targetPath = fakePath("added.txt")
    val targetChange = Change(null, CurrentContentRevision.create(targetPath))
    val anotherChange = Change(null, CurrentContentRevision.create(fakePath("another.txt")))
    val defaultList = LocalChangeListImpl.Builder(project, "Default")
      .setDefault(true)
      .setChanges(listOf(targetChange, anotherChange))
      .build()

    val model = buildModel(view, listOf(defaultList), emptyList())
    updateModelAndSelect(view, model, targetPath)
    val selectedChange = view.selectedChanges.single()
    assertEquals(
      "Selected change must correspond to the provided FilePath",
      targetPath,
      ChangesUtil.getAfterPath(selectedChange!!)
    )
  }

  fun `test selects change in multiple change lists`() {
    val view = LocalChangesListView(project)

    val targetPath = fakePath("default.txt")
    val change = Change(null, CurrentContentRevision.create(targetPath))
    val workList = LocalChangeListImpl.Builder(project, "Work").setDefault(false)
      .setChanges(
        listOf(
          change,
          Change(null, CurrentContentRevision.create(fakePath("work-other.txt"))),
        )
      )
      .build()

    val defaultList = LocalChangeListImpl.Builder(project, "Default")
      .setDefault(true)
      .setChanges(listOf(change))
      .build()

    val model = buildModel(view, listOf(defaultList, workList), emptyList())
    updateModelAndSelect(view, model, targetPath)

    val selectedChange = view.selectedChanges.single()!!
    assertEquals(targetPath, ChangesUtil.getAfterPath(selectedChange))
    val changelistNode = view.selectionPath!!.path[1] as DefaultMutableTreeNode
    assertEquals("Selected change must be under the Default changelist node", defaultList, changelistNode.userObject)
  }

  private fun buildModel(
    view: LocalChangesListView,
    changeLists: List<ChangeList>,
    unversionedFiles: List<FilePath>,
  ): DefaultTreeModel = TreeModelBuilder(project, view.grouping)
    .setChangeLists(changeLists, false, null)
    .setUnversioned(unversionedFiles)
    .build()

  private fun updateModelAndSelect(view: LocalChangesListView, model: DefaultTreeModel, pathToSelect: FilePath) {
    runInEdtAndWait {
      view.updateTreeModel(model, ChangesTree.ALWAYS_RESET)
      assertEquals(0, view.selectionCount)
      view.selectFile(pathToSelect)
      assertEquals(1, view.selectionCount)
    }
  }

  private fun fakePath(fileName: String): FilePath =
    VcsContextFactory.getInstance().createFilePath("/LocalChangesListViewSelectFileTest/$fileName", false)
}
