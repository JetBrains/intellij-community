// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.platform.debugger.impl.frontend.FrontendXDebuggerManager
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue
import com.intellij.xdebugger.impl.actions.areFrontendDebuggerActionsEnabled
import com.intellij.xdebugger.impl.actions.handlers.XMarkObjectActionHandler.Companion.performMarkObject
import com.intellij.xdebugger.impl.frame.XValueMarkers
import com.intellij.xdebugger.impl.rpc.XValueMarkerId
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import org.jetbrains.annotations.ApiStatus

/**
 * Frontend implementation of [com.intellij.xdebugger.impl.actions.MarkObjectAction]
 */
@ApiStatus.Internal
private class FrontendMarkObjectAction : AnAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun update(event: AnActionEvent) {
    if (!areFrontendDebuggerActionsEnabled()) {
      event.presentation.isEnabledAndVisible = false
      return
    }

    val markers = getMarkers(event) ?: run {
      event.presentation.isEnabledAndVisible = false
      return
    }
    val value = XDebuggerTreeActionBase.getSelectedValue(event.dataContext) ?: run {
      event.presentation.isEnabledAndVisible = false
      return
    }

    val canMark = markers.canMarkValue(value)
    if (!canMark) {
      event.presentation.isVisible = true
      event.presentation.isEnabled = false
      return
    }

    val isMarked = markers.getMarkup(value) != null
    val text = if (isMarked) {
      ActionsBundle.message("action.Debugger.MarkObject.unmark.text")
    }
    else {
      ActionsBundle.message("action.Debugger.MarkObject.text")
    }

    event.presentation.text = text
    event.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val markers = getMarkers(e)
    val node = XDebuggerTreeActionBase.getSelectedNode(e.dataContext)

    if (markers == null || node == null) {
      return
    }

    val treeState = XDebuggerTreeState.saveState(node.tree)

    performMarkObject(e, node, markers, onSuccess = {
      if (DebuggerUIUtil.isInDetachedTree(e)) {
        node.tree.rebuildAndRestore(treeState)
      }
    })
  }

  private fun getMarkers(e: AnActionEvent): XValueMarkers<FrontendXValue, XValueMarkerId>? {
    val project = e.project ?: return null
    return FrontendXDebuggerManager.getInstance(project).currentSession.value?.valueMarkers
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}