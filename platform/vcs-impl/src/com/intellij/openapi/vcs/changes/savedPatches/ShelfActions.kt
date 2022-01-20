// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager
import java.util.function.Supplier

abstract class ShelfAction : DumbAwareAction {
  constructor(): super()
  constructor(dynamicText: Supplier<@NlsActions.ActionText String>,
              dynamicDescription: Supplier<@NlsActions.ActionDescription String>) : super(dynamicText, dynamicDescription, null)

  abstract fun perform(project: Project, shelves: List<ShelvedChangeList>)

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null && !e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY).isNullOrEmpty()
    e.presentation.isVisible = e.isFromActionToolbar ||
                               (e.project != null && e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY) != null)
  }

  override fun actionPerformed(e: AnActionEvent) {
    FileDocumentManager.getInstance().saveAllDocuments()
    perform(e.project!!, e.getRequiredData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY))
  }
}

class ApplyShelfAction : ShelfAction(VcsBundle.messagePointer("saved.patch.apply.action"),
                                     VcsBundle.messagePointer("shelf.apply.action.description")) {
  override fun perform(project: Project, shelves: List<ShelvedChangeList>) {
    ShelveChangesManager.getInstance(project).unshelveSilentlyAsynchronously(project, shelves, emptyList(), emptyList(), null, false)
  }
}

class PopShelfAction : ShelfAction(VcsBundle.messagePointer("saved.patch.pop.action"),
                                   VcsBundle.messagePointer("shelf.pop.action.description")) {
  override fun perform(project: Project, shelves: List<ShelvedChangeList>) {
    ShelveChangesManager.getInstance(project).unshelveSilentlyAsynchronously(project, shelves, emptyList(), emptyList(), null, true)
  }
}

class DropShelfAction : ShelfAction(VcsBundle.messagePointer("shelf.drop.action"),
                                    VcsBundle.messagePointer("shelf.drop.action.description")) {
  override fun perform(project: Project, shelves: List<ShelvedChangeList>) {
    ShelvedChangesViewManager.deleteShelves(project, shelves)
  }
}

class ShelfOperationsGroup : SavedPatchesOperationsGroup() {
  override fun isApplicable(patchObject: SavedPatchesProvider.PatchObject<*>): Boolean {
    return patchObject.data is ShelvedChangeList
  }
}