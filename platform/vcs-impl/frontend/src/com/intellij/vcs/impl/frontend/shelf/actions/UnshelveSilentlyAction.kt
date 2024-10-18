// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.vcs.impl.frontend.shelf.ShelfService
import com.intellij.vcs.impl.frontend.shelf.tree.ShelfTree
import com.intellij.vcs.impl.frontend.shelf.tree.ShelfTree.Companion.GROUPED_CHANGES_KEY

class UnshelveSilentlyAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = getEventProject(e) ?: return
    val dataContext = e.dataContext
    val selectedChangelists = GROUPED_CHANGES_KEY.getData(dataContext)
    ShelfService.getInstance(project).unshelveSilently(selectedChangelists)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = getEventProject(e) != null && ShelfTree.SELECTED_CHANGELISTS_KEY.getData(e.dataContext) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
