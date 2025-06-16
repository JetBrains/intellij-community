// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame.actions

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.impl.frame.XWatchesView
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNodeImpl

private class XPauseWatchAction : XWatchesTreeActionBase(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isEnabled(e: AnActionEvent, tree: XDebuggerTree): Boolean {
    val selectedNodes = getSelectedNodes(tree, WatchNodeImpl::class.java)
    return selectedNodes.any { it.xWatch.canBePaused }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = e.presentation.isEnabled
    if (e.presentation.isEnabled) {
      val tree = XDebuggerTree.getTree(e) ?: return
      val selectedNodes = getSelectedNodes(tree, WatchNodeImpl::class.java)
        .filter { it.xWatch.canBePaused }
      // Resume action is shown only if all the breakpoints are paused
      val paused = selectedNodes.all { it.xWatch.isPaused }
      val watchText =
        if (selectedNodes.size > 1) XDebuggerBundle.message("xdebugger.watches")
        else XDebuggerBundle.message("xdebugger.watch")
      e.presentation.text =
        if (paused) XDebuggerBundle.message("action.XDebugger.ResumeWatch.template.text", watchText)
        else XDebuggerBundle.message("action.XDebugger.PauseWatch.template.text", watchText)
      e.presentation.icon = if (paused) AllIcons.Toolwindows.ToolWindowRun else AllIcons.Actions.Pause
    }
  }

  override fun perform(e: AnActionEvent, tree: XDebuggerTree, watchesView: XWatchesView) {
    val selectedNodes = getSelectedNodes(tree, WatchNodeImpl::class.java)
      .filter { it.xWatch.canBePaused }
    val paused = selectedNodes.all { it.xWatch.isPaused }
    for (node in selectedNodes) {
      // Skip nodes that are already in the correct state
      if (node.xWatch.isPaused != paused) continue
      node.xWatch.isPaused = !paused
      if (!paused) {
        // only update icon, keep calculated value
        node.setPresentation(AllIcons.Actions.Pause, node.valuePresentation!!, !node.isLeaf)
      }
      else {
        // resume watch, trigger value evaluation
        node.valueContainer.computePresentation(node, XValuePlace.TREE)
      }
    }
  }
}
