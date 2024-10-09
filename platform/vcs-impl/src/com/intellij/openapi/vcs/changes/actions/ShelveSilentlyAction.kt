// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import org.jetbrains.annotations.ApiStatus

internal class ShelveSilentlyAction : ShelveSilentlyActionBase(rollbackChanges = true)
internal class SaveToShelveAction : ShelveSilentlyActionBase(rollbackChanges = false)

@ApiStatus.Internal
abstract class ShelveSilentlyActionBase(val rollbackChanges: Boolean) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    val changes = e.getData(VcsDataKeys.CHANGES)

    e.presentation.isEnabled = project != null && !changes.isNullOrEmpty() &&
                               ChangeListManager.getInstance(project).areChangeListsEnabled()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val changes = e.getData(VcsDataKeys.CHANGES)!!

    FileDocumentManager.getInstance().saveAllDocuments()
    val shelveChangesManager = ShelveChangesManager.getInstance(project)
    shelveChangesManager.shelveSilentlyUnderProgress(changes.toList(), rollbackChanges)

    if (Registry.`is`("llm.vcs.shelve.title.generation")) {
      shelveChangesManager.showGotItTooltip(project, PlatformDataKeys.CONTEXT_COMPONENT.getData(e.dataContext))
    }
  }
}
