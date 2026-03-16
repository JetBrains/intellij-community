// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.changes

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vcs.changes.LocalChangeListImpl
import com.intellij.openapi.vcs.changes.LocalChangesListView
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitDetails
import com.intellij.platform.vcs.impl.shared.commit.insertEditedCommitNode
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.runInEdtAndWait
import javax.swing.tree.DefaultTreeModel

abstract class ChangesViewTestBase : LightPlatformTestCase() {
  protected lateinit var view: LocalChangesListView

  override fun setUp() {
    super.setUp()
    view = LocalChangesListView(project)
  }

  protected fun updateModelAndSelect(view: LocalChangesListView, model: DefaultTreeModel, toSelect: FilePath) {
    runInEdtAndWait {
      view.updateTreeModel(model, ChangesTree.ALWAYS_RESET)
      assertEquals(0, view.selectionCount)
      assertTrue(view.containsFile(toSelect))
      view.selectFile(toSelect)
      assertEquals(1, view.selectionCount)
    }
  }

  protected fun path(fileName: String): FilePath =
    VcsContextFactory.getInstance().createFilePath("/ChangesViewTest/$fileName", false)

  protected fun buildModel(
    view: LocalChangesListView,
    lists: List<LocalChangeListImpl>,
    unversioned: List<FilePath>,
    editedCommit: EditedCommitDetails? = null,
  ): DefaultTreeModel = TreeModelBuilder(project, view.grouping)
    .setChangeLists(lists, false, null)
    .setUnversioned(unversioned)
    .apply {
      if (editedCommit != null) {
        insertEditedCommitNode(this, editedCommit)
      }
    }
    .build()
}