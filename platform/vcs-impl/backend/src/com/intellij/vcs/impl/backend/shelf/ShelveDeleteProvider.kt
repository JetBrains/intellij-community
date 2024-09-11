// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf;

import com.intellij.ide.DeleteProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager
import com.intellij.util.ui.tree.TreeUtil

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class ShelveDeleteProvider(val project: Project, private val tree: ShelfTree) : DeleteProvider {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun deleteElement(dataContext: DataContext) {
    val shelvedListsToDelete = TreeUtil.collectSelectedObjectsOfType<ShelvedChangeList?>(tree, ShelvedChangeList::class.java)
    val shelvedListsFromChanges = ShelvedChangesViewManager.getShelvedLists(dataContext)
    val selectedChanges = ShelvedChangesViewManager.getShelveChanges(dataContext)
    val selectedBinaryChanges = ShelvedChangesViewManager.getBinaryShelveChanges(dataContext)

    ShelvedChangesViewManager.deleteShelves(project, shelvedListsToDelete, shelvedListsFromChanges, selectedChanges,
                                            selectedBinaryChanges)
  }

  override fun canDeleteElement(dataContext: DataContext): Boolean {
    return !ShelvedChangesViewManager.getShelvedLists(dataContext).isEmpty()
  }
}
