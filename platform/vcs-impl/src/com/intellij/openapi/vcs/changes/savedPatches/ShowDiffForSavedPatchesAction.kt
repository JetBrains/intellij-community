// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData

abstract class AbstractShowDiffForSavedPatchesAction : AnActionExtensionProvider {
  override fun isActive(e: AnActionEvent): Boolean {
    return e.getData(SavedPatchesUi.SAVED_PATCHES_UI) != null
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val changesBrowser = e.getData(SavedPatchesUi.SAVED_PATCHES_UI)?.changesBrowser
    if (project == null || changesBrowser == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isVisible = true
    val selection = if (e.getData(ChangesBrowserBase.DATA_KEY) == null) {
      VcsTreeModelData.all(changesBrowser.viewer)
    }
    else {
      VcsTreeModelData.selected(changesBrowser.viewer)
    }
    e.presentation.isEnabled = selection.userObjectsStream().anyMatch {
      getDiffRequestProducer(changesBrowser, it) != null
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val changesBrowser = e.getRequiredData(SavedPatchesUi.SAVED_PATCHES_UI).changesBrowser

    val selection = if (e.getData(ChangesBrowserBase.DATA_KEY) == null) {
      ListSelection.createAt(VcsTreeModelData.all(changesBrowser.viewer).userObjects(), 0)
    }
    else {
      VcsTreeModelData.getListSelectionOrAll(changesBrowser.viewer)
    }
    ChangesBrowserBase.showStandaloneDiff(e.project!!, changesBrowser, selection) { change ->
      getDiffRequestProducer(changesBrowser, change)
    }
  }

  abstract fun getDiffRequestProducer(changesBrowser: SavedPatchesChangesBrowser, userObject: Any): ChangeDiffRequestChain.Producer?
}

class ShowDiffForSavedPatchesAction : AbstractShowDiffForSavedPatchesAction() {
  override fun getDiffRequestProducer(changesBrowser: SavedPatchesChangesBrowser, userObject: Any): ChangeDiffRequestChain.Producer? {
    return changesBrowser.getDiffRequestProducer(userObject)
  }
}

class CompareWithLocalForSavedPatchesAction : AbstractShowDiffForSavedPatchesAction() {
  override fun getDiffRequestProducer(changesBrowser: SavedPatchesChangesBrowser, userObject: Any): ChangeDiffRequestChain.Producer? {
    return changesBrowser.getDiffWithLocalRequestProducer(userObject, false)
  }
}
