// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.SpecificFilesViewDialog.SpecificVirtualFilesViewDialog
import com.intellij.openapi.vcs.changes.ui.ChangesListView

internal class ModifiedWithoutEditingViewDialog(project: Project) :
  SpecificVirtualFilesViewDialog(project, VcsBundle.message("dialog.title.modified.without.checkout.files"),
                                 ChangesListView.MODIFIED_WITHOUT_EDITING_DATA_KEY,
                                 { ChangeListManager.getInstance(project).modifiedWithoutEditing }) {

  override fun addCustomActions(group: DefaultActionGroup) {
    val deleteAction = ActionManager.getInstance().getAction(IdeActions.ACTION_DELETE)
    val revertAction = ActionManager.getInstance().getAction(IdeActions.CHANGES_VIEW_ROLLBACK)
    deleteAction.registerCustomShortcutSet(myView, null)
    revertAction.registerCustomShortcutSet(myView, null)
    group.add(deleteAction)
    group.add(revertAction)
    myView.installPopupHandler(DefaultActionGroup(deleteAction, revertAction))
  }
}
