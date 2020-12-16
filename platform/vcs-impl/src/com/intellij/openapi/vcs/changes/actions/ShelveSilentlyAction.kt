// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager

class ShelveSilentlyAction : ShelveSilentlyActionBase(rollbackChanges = true)
class SaveToShelveAction : ShelveSilentlyActionBase(rollbackChanges = false)

abstract class ShelveSilentlyActionBase(val rollbackChanges: Boolean) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    val changes = e.getData(VcsDataKeys.CHANGES)

    e.presentation.isEnabled = project != null && !changes.isNullOrEmpty() &&
                               ChangeListManager.getInstance(project).areChangeListsEnabled()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val changes = e.getData(VcsDataKeys.CHANGES)!!

    FileDocumentManager.getInstance().saveAllDocuments()
    ShelveChangesManager.getInstance(project).shelveSilentlyUnderProgress(changes.toList(), rollbackChanges)
  }
}