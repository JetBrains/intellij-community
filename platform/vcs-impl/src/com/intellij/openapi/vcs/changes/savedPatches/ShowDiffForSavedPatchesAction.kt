// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class AbstractShowDiffForSavedPatchesAction : AnActionExtensionProvider {
  override fun isActive(e: AnActionEvent): Boolean {
    return e.getData(SavedPatchesUi.SAVED_PATCHES_UI) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val changesBrowser = e.getData(SavedPatchesUi.SAVED_PATCHES_UI)?.changesBrowser
    if (project == null || changesBrowser == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val isVisible = isVisible(changesBrowser)
    e.presentation.isVisible = isVisible

    if (isVisible) {
      val selection = if (e.getData(ChangesBrowserBase.DATA_KEY) == null) {
        VcsTreeModelData.all(changesBrowser.viewer)
      }
      else {
        VcsTreeModelData.selected(changesBrowser.viewer)
      }
      e.presentation.isEnabled = selection.iterateUserObjects().any {
        getDiffRequestProducer(changesBrowser, it) != null
      }
    }
  }

  protected open fun isVisible(changesBrowser: SavedPatchesChangesBrowser) = true

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val browser = e.getData(SavedPatchesUi.SAVED_PATCHES_UI)?.changesBrowser ?: return

    val selection = if (e.getData(ChangesBrowserBase.DATA_KEY) == null) {
      ListSelection.createAt(VcsTreeModelData.all(browser.viewer).userObjects(), 0)
    }
    else {
      VcsTreeModelData.getListSelectionOrAll(browser.viewer)
    }
    ChangesBrowserBase.showStandaloneDiff(project, browser, selection) { change ->
      getDiffRequestProducer(browser, change)
    }
  }

  abstract fun getDiffRequestProducer(changesBrowser: SavedPatchesChangesBrowser, userObject: Any): ChangeDiffRequestChain.Producer?
}

@ApiStatus.Internal
class ShowDiffForSavedPatchesAction : AbstractShowDiffForSavedPatchesAction() {
  override fun getDiffRequestProducer(changesBrowser: SavedPatchesChangesBrowser, userObject: Any): ChangeDiffRequestChain.Producer? {
    return changesBrowser.getDiffRequestProducer(userObject)
  }
}

@ApiStatus.Internal
class CompareWithLocalForSavedPatchesAction : AbstractShowDiffForSavedPatchesAction() {
  override fun getDiffRequestProducer(changesBrowser: SavedPatchesChangesBrowser, userObject: Any): ChangeDiffRequestChain.Producer? {
    return changesBrowser.getDiffWithLocalRequestProducer(userObject, false)
  }

  override fun isVisible(changesBrowser: SavedPatchesChangesBrowser): Boolean {
    return !changesBrowser.isShowDiffWithLocal()
  }
}

@ApiStatus.Internal
class CompareBeforeWithLocalForSavedPatchesAction : AbstractShowDiffForSavedPatchesAction() {
  override fun getDiffRequestProducer(changesBrowser: SavedPatchesChangesBrowser, userObject: Any): ChangeDiffRequestChain.Producer? {
    return changesBrowser.getDiffWithLocalRequestProducer(userObject, true)
  }
}
