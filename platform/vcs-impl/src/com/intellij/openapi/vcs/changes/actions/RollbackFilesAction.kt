// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.ActionPlaces.CHANGES_VIEW_TOOLBAR
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog
import com.intellij.vcs.commit.CommitMode
import com.intellij.vcs.commit.cleanActionText
import com.intellij.vcs.commit.getProjectCommitMode
import com.intellij.vcsUtil.RollbackUtil.getRollbackOperationName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RollbackFilesAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false

    if (!Manager.isPreferCheckboxesOverSelection()) return
    val project = e.project ?: return
    if (e.getProjectCommitMode() !is CommitMode.NonModalCommitMode) return
    if (e.getData(ChangesListView.DATA_KEY) == null) return

    val changes = e.getData(VcsDataKeys.CHANGES) ?: return
    with(e.presentation) {
      isVisible = true
      isEnabled = changes.isNotEmpty()
      text = message("action.for.file.with.dialog.text", getRollbackOperationName(project), changes.size)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (!Manager.checkClmActive(e)) return

    val project = e.project ?: return
    val changes = e.getData(VcsDataKeys.CHANGES) ?: return

    FileDocumentManager.getInstance().saveAllDocuments()
    RollbackChangesDialog.rollbackChanges(project, changes.asList())
  }

  object Manager {
    @JvmStatic
    fun checkClmActive(e: AnActionEvent): Boolean {
      val project = e.project ?: return false
      val title =
        if (CHANGES_VIEW_TOOLBAR == e.place) null
        else message("error.cant.perform.operation.now", cleanActionText(getRollbackOperationName(project)))

      return !ChangeListManager.getInstance(project).isFreezedWithNotification(title)
    }

    @JvmStatic
    fun isPreferCheckboxesOverSelection(): Boolean = Registry.`is`("vcs.prefer.checkboxes.over.selection")
  }
}