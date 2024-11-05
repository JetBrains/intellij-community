// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.frontend.shelf.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.vcs.impl.frontend.shelf.ShelfTreeUpdater
import com.intellij.vcs.impl.frontend.shelf.tree.ShelfTree.Companion.SELECTED_CHANGELISTS_KEY
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RenameShelvedChangelistAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val changeList = SELECTED_CHANGELISTS_KEY.getData(e.dataContext)?.singleOrNull() ?: return
    ShelfTreeUpdater.getInstance(project).startEditing(changeList)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = SELECTED_CHANGELISTS_KEY.getData(e.dataContext)?.size == 1
  }
}