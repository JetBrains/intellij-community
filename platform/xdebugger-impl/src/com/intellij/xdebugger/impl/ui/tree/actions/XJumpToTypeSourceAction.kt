// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.platform.debugger.impl.rpc.XDebuggerNavigationApi
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl

private class XJumpToTypeSourceAction : XJumpToSourceActionBase(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  override suspend fun navigateToSource(xValueId: XValueId): Boolean =
    XDebuggerNavigationApi.getInstance().navigateToXValueType(xValueId).await()

  override fun isEnabled(node: XValueNodeImpl, e: AnActionEvent): Boolean {
    return super.isEnabled(node, e) && node.valueContainer.canNavigateToTypeSource()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
