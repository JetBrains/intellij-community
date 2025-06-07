// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf.tree

import com.intellij.ide.DeleteProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.vcs.impl.frontend.shelf.ShelfService
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ShelfTree.Companion.GROUPED_CHANGES_KEY
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class ShelveDeleteProvider(val project: Project, private val tree: ShelfTree) : DeleteProvider {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun deleteElement(dataContext: DataContext) {
    val listsToDelete = tree.getExactlySelectedLists()

    val groupedChanges = GROUPED_CHANGES_KEY.getData(dataContext) ?: return
    ShelfService.getInstance(project).deleteChangeList(listsToDelete, groupedChanges)
  }

  override fun canDeleteElement(dataContext: DataContext): Boolean {
    return tree.getSelectedLists().isNotEmpty()
  }
}