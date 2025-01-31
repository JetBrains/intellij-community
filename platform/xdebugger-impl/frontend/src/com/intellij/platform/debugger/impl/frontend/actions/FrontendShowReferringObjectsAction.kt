// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue
import com.intellij.xdebugger.impl.actions.areFrontendDebuggerActionsEnabled
import com.intellij.xdebugger.impl.rpc.XDebuggerLuxApi
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private class FrontendShowReferringObjectsAction : XDebuggerTreeActionBase(), ActionRemoteBehaviorSpecification.Frontend {
  override fun update(e: AnActionEvent) {
    if (!areFrontendDebuggerActionsEnabled()) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    super.update(e)
  }

  override fun isEnabled(node: XValueNodeImpl, e: AnActionEvent): Boolean {
    return node.valueContainer is FrontendXValue
  }

  override fun perform(node: XValueNodeImpl, nodeName: String, e: AnActionEvent) {
    val frontendValue = node.valueContainer as? FrontendXValue ?: return

    service<FrontendShowReferringObjectsActionCoroutineScope>().cs.launch {
      XDebuggerLuxApi.getInstance().showReferringObjectsDialog(frontendValue.xValueDto.id, nodeName)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

@Service(Service.Level.APP)
private class FrontendShowReferringObjectsActionCoroutineScope(val cs: CoroutineScope)