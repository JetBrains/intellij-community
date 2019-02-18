// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.EditChangelistDialog
import com.intellij.util.ArrayUtil

class RenameChangeListAction : AnAction(), DumbAware {

  override fun update(e: AnActionEvent) {
    val target = getTargetChangeList(e)
    val visible = target != null && !target.isReadOnly
    e.presentation.isEnabled = visible
    if (e.place == ActionPlaces.CHANGES_VIEW_POPUP) {
      e.presentation.isVisible = visible
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val target = getTargetChangeList(e)
    if (target != null) {
      EditChangelistDialog(project, target).show()
    }
  }

  private fun getTargetChangeList(e: AnActionEvent): LocalChangeList? {
    val project = e.project ?: return null
    val lists = e.getData(VcsDataKeys.CHANGE_LISTS)
    if (!ArrayUtil.isEmpty(lists)) {
      return if (lists!!.size == 1) {
        ChangeListManager.getInstance(project).findChangeList(lists[0].name)
      }
      else null
    }
    val changes = e.getData(VcsDataKeys.CHANGES) ?: return null

    var result: LocalChangeList? = null
    for (change in changes) {
      val cl = ChangeListManager.getInstance(project).getChangeList(change)
      if (result == null)
        result = cl
      else if (cl != null && cl != result)
        return null
    }
    return result
  }
}