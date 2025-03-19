// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.actions.areFrontendDebuggerActionsEnabled
import com.intellij.xdebugger.impl.rpc.XDebuggerNavigationApi
import com.intellij.xdebugger.impl.ui.tree.actions.XJumpToSourceActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl

private class FrontendXJumpToSourceAction : XJumpToSourceActionBase(), ActionRemoteBehaviorSpecification.Frontend {
  override fun update(e: AnActionEvent) {
    if (!areFrontendDebuggerActionsEnabled()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    super.update(e)
  }

  override fun isEnabled(node: XValueNodeImpl, e: AnActionEvent): Boolean {
    return super.isEnabled(node, e) && node.valueContainer.canNavigateToSource()
  }

  override suspend fun navigateToSource(project: Project, value: XValue): Boolean {
    val frontendValue = value as? FrontendXValue ?: return false
    return XDebuggerNavigationApi.getInstance().navigateToXValue(frontendValue.xValueDto.id).await()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}