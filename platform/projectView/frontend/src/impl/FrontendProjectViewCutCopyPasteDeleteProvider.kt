// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.impl

import com.intellij.ide.CopyProvider
import com.intellij.ide.CutProvider
import com.intellij.ide.DeleteProvider
import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.platform.projectView.pane.PROJECT_VIEW_SELECTED_NODE_IDS_KEY

/**
 * The frontend half of the Project View cut/copy/paste/delete support.
 *
 * These actions are frontend actions that delegate their work to the backend: the enabled state is
 * answered here, from the selected node IDs alone, and the actual work is sent to the backend over the
 * pane request channel, because it needs PSI. That keeps the data context of the Project View pane
 * purely frontend-side, which is what makes the same code work in monolith, light and split modes.
 *
 * Note that the enabled state is deliberately approximate: unlike [com.intellij.ide.CopyPasteDelegator]
 * we can't consult `CopyHandler.canCopy`/`MoveHandler.canMove` here, so a node that can't actually be
 * copied or moved still looks enabled and invoking it does nothing. The backend re-checks everything
 * before doing any work.
 */
// The PasteProvider restriction is about implementing the com.intellij.customPasteProvider extension point,
// which does need PSI and therefore the backend. This provider is not registered on that extension point:
// it is published as PlatformDataKeys.PASTE_PROVIDER by the pane and delegates the paste to the backend.
@Suppress("SplitModeApiUsage")
internal class FrontendProjectViewCutCopyPasteDeleteProvider(
  private val paneTreeModel: FrontendProjectViewPaneTreeModel,
) : CopyProvider, CutProvider, PasteProvider, DeleteProvider {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isCopyVisible(dataContext: DataContext): Boolean = true

  override fun isCopyEnabled(dataContext: DataContext): Boolean = selectedNodeIds(dataContext).isNotEmpty()

  override fun performCopy(dataContext: DataContext) {
    val nodeIds = selectedNodeIds(dataContext)
    if (nodeIds.isEmpty()) return
    paneTreeModel.requestCopy(nodeIds)
  }

  override fun isCutVisible(dataContext: DataContext): Boolean = true

  override fun isCutEnabled(dataContext: DataContext): Boolean = selectedNodeIds(dataContext).isNotEmpty()

  override fun performCut(dataContext: DataContext) {
    val nodeIds = selectedNodeIds(dataContext)
    if (nodeIds.isEmpty()) return
    paneTreeModel.requestCut(nodeIds)
  }

  // Both are true because only the backend knows whether the clipboard holds anything pasteable, and
  // PasteAction consults isPastePossible on update and isPasteEnabled on perform. This is the same
  // behavior as the monolith Project View, where isPastePossible is hardcoded to true as well.
  override fun isPastePossible(dataContext: DataContext): Boolean = true

  override fun isPasteEnabled(dataContext: DataContext): Boolean = true

  override fun performPaste(dataContext: DataContext) {
    val nodeIds = selectedNodeIds(dataContext)
    if (nodeIds.isEmpty()) return
    paneTreeModel.requestPaste(nodeIds)
  }

  override fun canDeleteElement(dataContext: DataContext): Boolean = selectedNodeIds(dataContext).isNotEmpty()

  override fun deleteElement(dataContext: DataContext) {
    val nodeIds = selectedNodeIds(dataContext)
    if (nodeIds.isEmpty()) return
    paneTreeModel.requestDelete(nodeIds)
  }

  private fun selectedNodeIds(dataContext: DataContext): List<Long> =
    PROJECT_VIEW_SELECTED_NODE_IDS_KEY.getData(dataContext) ?: emptyList()
}
