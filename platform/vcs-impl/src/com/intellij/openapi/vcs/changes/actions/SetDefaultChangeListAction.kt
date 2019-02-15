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

class SetDefaultChangeListAction : AnAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    val lists = e.getData(VcsDataKeys.CHANGE_LISTS)
    val visible = lists != null && lists.size == 1 && lists[0] is LocalChangeList && !(lists[0] as LocalChangeList).isDefault
    e.presentation.isEnabled = visible
    if (e.place == ActionPlaces.CHANGES_VIEW_POPUP) {
      e.presentation.isVisible = visible
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val lists = e.getData(VcsDataKeys.CHANGE_LISTS)!!
    ChangeListManager.getInstance(project!!).defaultChangeList = lists[0] as LocalChangeList
  }
}
