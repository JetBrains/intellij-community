// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.debugger.settings

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.xdebugger.impl.frame.actions.XWatchesTreeActionBase.getSelectedNodes
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import org.jetbrains.idea.devkit.debugger.DevKitDebuggerBundle

private class DisableIDEStateAction : AnAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.EDT
  override fun update(e: AnActionEvent) {
    if (!DevKitDebuggerSettings.getInstance().showIdeState) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isEnabledAndVisible = ideNodeSelected(e)
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (!ideNodeSelected(e)) return
    val tree = XDebuggerTree.getTree(e) ?: return
    DevKitDebuggerSettings.getInstance().showIdeState = false
    tree.rebuildAndRestore(XDebuggerTreeState.saveState(tree))
  }
}

private fun ideNodeSelected(e: AnActionEvent): Boolean {
  val tree = XDebuggerTree.getTree(e) ?: return false
  val selectedNodes = getSelectedNodes(tree, XValueNodeImpl::class.java)
  if (selectedNodes.isEmpty()) return false
  return selectedNodes.any { it.name == DevKitDebuggerBundle.message("debugger.ide.state") }
}
