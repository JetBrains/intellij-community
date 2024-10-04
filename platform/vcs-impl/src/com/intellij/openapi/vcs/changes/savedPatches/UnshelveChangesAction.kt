// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.shelf.ShelvedWrapper
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class UnshelveChangesActionBase(@NlsActions.ActionText text: String, @NlsActions.ActionDescription description: String,
                                         private val removeFromShelf: Boolean) : DumbAwareAction(text, description, null) {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    if (e.project == null || selectedChangeList(e) == null) {
      e.presentation.isEnabledAndVisible = false
    }
    e.presentation.isVisible = true
    e.presentation.isEnabled = selectedChanges(e)?.firstOrNull() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val selectedChangeList = selectedChangeList(e) ?: return
    val selectedChanges = selectedChanges(e)?.toList() ?: return

    FileDocumentManager.getInstance().saveAllDocuments()
    ShelveChangesManager.getInstance(project).unshelveSilentlyAsynchronously(project,
                                                                             listOf(selectedChangeList),
                                                                             selectedChanges.mapNotNull { it.shelvedChange },
                                                                             selectedChanges.mapNotNull { it.binaryFile },
                                                                             null,
                                                                             removeFromShelf)
  }

  private fun selectedChangeList(e: AnActionEvent): ShelvedChangeList? {
    return e.getData(SavedPatchesUi.SAVED_PATCH_SELECTED_PATCH)?.data as? ShelvedChangeList
  }

  private fun selectedChanges(e: AnActionEvent): Sequence<ShelvedWrapper>? {
    return e.getData(SavedPatchesUi.SAVED_PATCH_SELECTED_CHANGES)?.asSequence()?.filterIsInstance(ShelvedWrapper::class.java)
  }
}

@ApiStatus.Internal
class UnshelveChangesAction : UnshelveChangesActionBase(VcsBundle.message("shelf.unshelve.changes.action.text"),
                                                        VcsBundle.message("shelf.unshelve.changes.action.description"),
                                                        false)

@ApiStatus.Internal
class UnshelveChangesAndRemoveAction : UnshelveChangesActionBase(VcsBundle.message("shelf.unshelve.changes.remove.action.text"),
                                                                 VcsBundle.message("shelf.unshelve.changes.remove.action.description"),
                                                                 true)