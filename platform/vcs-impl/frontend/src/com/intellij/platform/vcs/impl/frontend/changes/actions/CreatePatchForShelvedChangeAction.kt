// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.util.containers.ContainerUtil
import com.intellij.platform.vcs.impl.frontend.changes.CHANGE_LISTS_KEY
import com.intellij.platform.vcs.impl.frontend.changes.ChangeList
import com.intellij.platform.vcs.impl.frontend.shelf.ShelfService
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ShelfTree
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class CreatePatchForShelvedChangeAction(private val silentClipboard: Boolean) : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val changeLists = e.getData(CHANGE_LISTS_KEY) ?: return
    val shelvedChangeList = changeLists.singleOrNull()
    val shelfTree = ShelfTree.SHELVED_CHANGES_TREE_KEY.getData(e.dataContext)
    val shelfService = ShelfService.getInstance(project)
    if (shelfTree != null) {
      if (shelvedChangeList != null) {
        val entireList = shelfTree.getExactlySelectedLists().singleOrNull() != null
        val selectedChanges = if (entireList) ContainerUtil.emptyList() else shelvedChangeList.changes
        shelfService.createPatch(listOf(ChangeList(shelvedChangeList.changeListNode, selectedChanges)), silentClipboard)
      }
      else {
        shelfService.createPatch(changeLists, silentClipboard)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val changeLists = e.getData(CHANGE_LISTS_KEY) ?: emptySet()
    if (changeLists.isEmpty()) {
      e.presentation.isEnabled = false
      return
    }
    val changelistNum = changeLists.size
    if (changelistNum > 1) {
      e.presentation.isEnabled = false
      return
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

@ApiStatus.Internal
class CreatePatchForShelvedChangeActionClipboard : CreatePatchForShelvedChangeAction(true)

@ApiStatus.Internal
class CreatePatchForShelvedChangeActionDialog : CreatePatchForShelvedChangeAction(false)