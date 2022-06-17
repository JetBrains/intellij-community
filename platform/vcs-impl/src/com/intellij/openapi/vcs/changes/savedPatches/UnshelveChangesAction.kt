// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.shelf.ShelvedWrapper

abstract class UnshelveChangesActionBase(@NlsActions.ActionText text: String, @NlsActions.ActionDescription description: String,
                                         private val removeFromShelf: Boolean) : DumbAwareAction(text, description, null) {

  override fun update(e: AnActionEvent) {
    if (e.project == null || selectedChangeList(e) == null) {
      e.presentation.isEnabledAndVisible = false
    }
    e.presentation.isVisible = true
    e.presentation.isEnabled = !selectedChanges(e).isNullOrEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val selectedChangeList = selectedChangeList(e) ?: return
    val selectedChanges = selectedChanges(e) ?: return

    FileDocumentManager.getInstance().saveAllDocuments()
    ShelveChangesManager.getInstance(project).unshelveSilentlyAsynchronously(project,
                                                                             listOf(selectedChangeList),
                                                                             selectedChanges.mapNotNull { it.shelvedChange },
                                                                             selectedChanges.mapNotNull { it.binaryFile },
                                                                             null,
                                                                             removeFromShelf)
  }

  private fun selectedChangeList(e: AnActionEvent): ShelvedChangeList? {
    return e.getData(SavedPatchesUi.SAVED_PATCHES_UI)?.selectedPatchObjectOrNull()?.data as? ShelvedChangeList
  }

  private fun selectedChanges(e: AnActionEvent): List<ShelvedWrapper>? {
    return e.getData(SavedPatchesUi.SAVED_PATCH_SELECTED_CHANGES)?.filterIsInstance(ShelvedWrapper::class.java)
  }
}

class UnshelveChangesAction : UnshelveChangesActionBase(VcsBundle.message("shelf.unshelve.changes.action.text"),
                                                        VcsBundle.message("shelf.unshelve.changes.action.description"),
                                                        false)

class UnshelveChangesAndRemoveAction : UnshelveChangesActionBase(VcsBundle.message("shelf.unshelve.changes.remove.action.text"),
                                                                 VcsBundle.message("shelf.unshelve.changes.remove.action.description"),
                                                                 true)