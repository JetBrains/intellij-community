// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.ide.CommonActionsManager
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import org.jetbrains.annotations.ApiStatus

// TODO IJPL-173924:
//  Replace with a proper abstraction once actions are fully supported in RD mode
//  Proper handle for not loaded actions in split mode
@ApiStatus.Internal
@ApiStatus.Obsolete
object ChangesViewPanelActions {
  @JvmStatic
  fun initActions(changesViewPanel: ChangesViewPanel) {
    changesViewPanel.toolbarActionGroup.addAll(createChangesToolbarActions(changesViewPanel.changesView))
  }

  private fun createChangesToolbarActions(clView: ChangesListView): List<AnAction> {
    val actions = mutableListOf<AnAction?>()
    actions.add(CustomActionsSchema.getInstance().getCorrectedAction(ActionPlaces.CHANGES_VIEW_TOOLBAR))

    if (!isNewUI()) {
      actions.add(Separator.getInstance())
    }

    actions.add(ActionManager.getInstance().getAction("ChangesView.ViewOptions"))
    actions.add(CommonActionsManager.getInstance().createExpandAllHeaderAction(clView.treeExpander, clView))
    actions.add(CommonActionsManager.getInstance().createCollapseAllAction(clView.treeExpander, clView))
    actions.add(Separator.getInstance())
    actions.add(ActionManager.getInstance().getAction("ChangesView.SingleClickPreview"))
    actions.add(ActionManager.getInstance().getAction("Vcs.GroupedDiffToolbarAction"))
    actions.add(Separator.getInstance())

    return actions.filterNotNull()
  }
}