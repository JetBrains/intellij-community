// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.LocalChangeListImpl
import com.intellij.platform.vcs.changes.ChangesUtil
import com.intellij.platform.vcs.impl.changes.ChangesViewTestBase
import javax.swing.tree.DefaultMutableTreeNode

internal class LocalChangesListViewSelectFileTest : ChangesViewTestBase() {
  fun `test selects unversioned file path`() {
    val unversionedPath = path("unversioned.txt")
    val defaultList = LocalChangeListImpl.Builder(project, "Default")
      .setDefault(true)
      .setChanges(listOf(Change(null, CurrentContentRevision.create(path("default-change.txt")))))
      .build()

    val model = buildModel(view, listOf(defaultList), listOf(unversionedPath))
    updateModelAndSelect(view, model, unversionedPath)

    assertEquals(unversionedPath, view.selectedUnversionedFiles.single())
  }

  fun `test selects change by file path`() {
    val targetPath = path("added.txt")
    val targetChange = Change(null, CurrentContentRevision.create(targetPath))
    val anotherChange = Change(null, CurrentContentRevision.create(path("another.txt")))
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
    val targetPath = path("default.txt")
    val change = Change(null, CurrentContentRevision.create(targetPath))
    val workList = LocalChangeListImpl.Builder(project, "Work").setDefault(false)
      .setChanges(
        listOf(
          change,
          Change(null, CurrentContentRevision.create(path("work-other.txt"))),
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
}
