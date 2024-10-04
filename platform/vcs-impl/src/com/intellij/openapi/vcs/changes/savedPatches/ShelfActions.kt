// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangesViewManager
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

@ApiStatus.Internal
abstract class ShelfAction(
  dynamicText: Supplier<@NlsActions.ActionText String>,
  dynamicDescription: Supplier<@NlsActions.ActionDescription String>)
  : DumbAwareAction(dynamicText, dynamicDescription) {

  abstract fun perform(project: Project, shelves: List<ShelvedChangeList>)

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null && !ShelvedChangesViewManager.getShelvedLists(e.dataContext).isEmpty()
    e.presentation.isVisible = e.isFromActionToolbar ||
                               (e.project != null && e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY) != null)
  }

  override fun actionPerformed(e: AnActionEvent) {
    FileDocumentManager.getInstance().saveAllDocuments()
    perform(e.project!!, ShelvedChangesViewManager.getShelvedLists(e.dataContext))
  }
}

@ApiStatus.Internal
class ApplyShelfAction : ShelfAction(VcsBundle.messagePointer("saved.patch.apply.action"),
                                     VcsBundle.messagePointer("shelf.apply.action.description")) {
  override fun perform(project: Project, shelves: List<ShelvedChangeList>) {
    ShelveChangesManager.getInstance(project).unshelveSilentlyAsynchronously(project, shelves, emptyList(), emptyList(), null, false)
  }
}

@ApiStatus.Internal
class PopShelfAction : ShelfAction(VcsBundle.messagePointer("saved.patch.pop.action"),
                                   VcsBundle.messagePointer("shelf.pop.action.description")) {
  override fun perform(project: Project, shelves: List<ShelvedChangeList>) {
    ShelveChangesManager.getInstance(project).unshelveSilentlyAsynchronously(project, shelves, emptyList(), emptyList(), null, true)
  }
}

@ApiStatus.Internal
class DropShelfAction : ShelfAction(VcsBundle.messagePointer("shelf.drop.action"),
                                    VcsBundle.messagePointer("shelf.drop.action.description")) {
  override fun perform(project: Project, shelves: List<ShelvedChangeList>) {
    ShelvedChangesViewManager.deleteShelves(project, shelves)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    if (e.presentation.isEnabled) {
      val shelfTree = e.getData(ShelvedChangesViewManager.SHELVED_CHANGES_TREE)
      e.presentation.isEnabled = shelfTree == null || !shelfTree.isEditing
    }
  }
}

@ApiStatus.Internal
class ShelfOperationsGroup : SavedPatchesOperationsGroup() {
  override fun isApplicable(patchObject: SavedPatchesProvider.PatchObject<*>): Boolean {
    return patchObject.data is ShelvedChangeList
  }
}