// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.EditChangelistDialog

class RenameChangeListAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val changeList = getTargetChangeList(e)
    val enabled = changeList != null && !changeList.isReadOnly

    e.presentation.isEnabled = enabled
    if (e.place == ActionPlaces.CHANGES_VIEW_POPUP) {
      e.presentation.isVisible = enabled
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val changeList = getTargetChangeList(e)

    if (changeList != null) {
      EditChangelistDialog(project, changeList).show()
    }
  }

  private fun getTargetChangeList(e: AnActionEvent): LocalChangeList? {
    val changeListManager = ChangeListManager.getInstance(e.project ?: return null)

    val changeLists = e.getData(VcsDataKeys.CHANGE_LISTS)
    if (!changeLists.isNullOrEmpty()) return changeLists.singleOrNull()?.let { changeListManager.findChangeList(it.name) }

    val changes = e.getData(VcsDataKeys.CHANGES) ?: return null
    return changes.asSequence().mapNotNull { changeListManager.getChangeList(it) }.distinct().singleOrNull()
  }
}