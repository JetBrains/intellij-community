// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.vcs.impl.frontend.shelf.ShelfService
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ShelfTree
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ShelfTree.Companion.GROUPED_CHANGES_KEY
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class FrontendUnshelveAction(private val withDialog: Boolean) : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = getEventProject(e) ?: return
    val dataContext = e.dataContext
    val selectedChangelists = GROUPED_CHANGES_KEY.getData(dataContext) ?: return
    ShelfService.getInstance(project).unshelve(selectedChangelists, withDialog)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = getEventProject(e) != null && !ShelfTree.SELECTED_CHANGELISTS_KEY.getData(e.dataContext).isNullOrEmpty()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
@ApiStatus.Internal
class UnshelveWithDialogAction() : FrontendUnshelveAction(true)
@ApiStatus.Internal
class UnshelveSilentlyAction() : FrontendUnshelveAction(false)