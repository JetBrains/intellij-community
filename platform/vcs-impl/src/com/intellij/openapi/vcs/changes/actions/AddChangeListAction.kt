// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ui.NewChangelistDialog

class AddChangeListAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getData(CommonDataKeys.PROJECT)
    val dlg = NewChangelistDialog(project)
    dlg.show()
    if (dlg.exitCode == DialogWrapper.OK_EXIT_CODE) {
      val list = ChangeListManager.getInstance(project!!).addChangeList(dlg.name, dlg.description)
      if (dlg.isNewChangelistActive) {
        ChangeListManager.getInstance(project).defaultChangeList = list
      }
      dlg.panel.changelistCreatedOrChanged(list)
    }
  }

  override fun update(e: AnActionEvent) {
    if (e.place == ActionPlaces.CHANGES_VIEW_POPUP) {
      val lists = e.getData(VcsDataKeys.CHANGE_LISTS)
      e.presentation.isVisible = lists != null && lists.size > 0
    }
  }
}