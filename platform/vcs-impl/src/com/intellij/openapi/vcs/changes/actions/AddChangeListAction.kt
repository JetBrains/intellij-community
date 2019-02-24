// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ui.NewChangelistDialog

class AddChangeListAction : AbstractChangeListAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val dialog = NewChangelistDialog(project)

    if (dialog.showAndGet()) {
      val changeListManager = ChangeListManager.getInstance(project)
      val changeList = changeListManager.addChangeList(dialog.name, dialog.description)

      if (dialog.isNewChangelistActive) {
        changeListManager.defaultChangeList = changeList
      }
      dialog.panel.changelistCreatedOrChanged(changeList)
    }
  }

  override fun update(e: AnActionEvent) {
    val enabled = e.project != null

    e.presentation.isEnabled = enabled
    if (e.place == ActionPlaces.CHANGES_VIEW_POPUP) {
      e.presentation.isVisible = enabled && !getChangeLists(e).none()
    }
  }
}