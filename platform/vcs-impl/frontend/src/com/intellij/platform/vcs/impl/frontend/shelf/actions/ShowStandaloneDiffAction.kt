// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.vcs.impl.frontend.changes.CHANGE_LISTS_KEY
import com.intellij.platform.vcs.impl.frontend.shelf.ShelfService
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ShelfTree
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class ShowStandaloneDiffAction(private val withLocal: Boolean) : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = getEventProject(e) ?: return
    val dataContext = e.dataContext
    val selectedChangelists = ShelfTree.Companion.GROUPED_CHANGES_KEY.getData(dataContext) ?: return
    ShelfService.getInstance(project).compareWithLocal(selectedChangelists, withLocal)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = !CHANGE_LISTS_KEY.getData(e.dataContext).isNullOrEmpty()
  }
}

@ApiStatus.Internal
class CompareWithLocalAction : ShowStandaloneDiffAction(true)
@ApiStatus.Internal
class ShowDifInNewWindowAction : ShowStandaloneDiffAction(false)