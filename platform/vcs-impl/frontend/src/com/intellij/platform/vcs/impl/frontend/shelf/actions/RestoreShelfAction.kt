// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.vcs.impl.frontend.shelf.ShelfService
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ShelfTree
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RestoreShelfAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val changeLists = ShelfTree.Companion.SELECTED_DELETED_CHANGELISTS_KEY.getData(e.dataContext) ?: return
    ShelfService.getInstance(project).restoreShelves(changeLists)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = ShelfTree.Companion.SELECTED_DELETED_CHANGELISTS_KEY.getData(e.dataContext)?.isNotEmpty() == true
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}