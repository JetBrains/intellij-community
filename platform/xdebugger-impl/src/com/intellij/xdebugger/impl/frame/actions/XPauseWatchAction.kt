// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame.actions

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.impl.frame.XWatchesView
import com.intellij.xdebugger.impl.frame.actions.XWatchesTreeActionBase.getSelectedNodes
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNodeImpl

private class XPauseWatchAction : XWatchesTreeActionBase(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isEnabled(e: AnActionEvent, tree: XDebuggerTree): Boolean {
    val selectedNode = getSelectedWatchNode(tree) ?: return false
    return selectedNode.xWatch.canBePaused
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = e.presentation.isEnabled
    if (e.presentation.isEnabled) {
      val tree = XDebuggerTree.getTree(e) ?: return
      val node = getSelectedWatchNode(tree) ?: return
      val paused = node.xWatch.isPaused
      e.presentation.text =
        if (paused) ActionsBundle.message("action.XDebugger.ResumeWatch.text")
        else ActionsBundle.message("action.XDebugger.PauseWatch.text")
      e.presentation.icon = if (paused) AllIcons.Toolwindows.ToolWindowRun else AllIcons.Actions.Pause
    }
  }

  override fun perform(e: AnActionEvent, tree: XDebuggerTree, watchesView: XWatchesView) {
    val node = getSelectedWatchNode(tree) ?: return
    val paused = node.xWatch.isPaused
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

private fun getSelectedWatchNode(tree: XDebuggerTree): WatchNodeImpl? =
  getSelectedNodes(tree, WatchNodeImpl::class.java).singleOrNull()
