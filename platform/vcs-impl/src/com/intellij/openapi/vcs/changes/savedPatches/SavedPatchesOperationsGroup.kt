// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware

abstract class SavedPatchesOperationsGroup: DefaultActionGroup(), DumbAware {
  protected abstract fun isApplicable(patchObject: SavedPatchesProvider.PatchObject<*>): Boolean

  override fun update(e: AnActionEvent) {
    val selectedPatch = e.getData(SavedPatchesUi.SAVED_PATCHES_UI)?.selectedPatchObjectOrNull()
    e.presentation.isEnabledAndVisible = selectedPatch != null && isApplicable(selectedPatch)
  }
}