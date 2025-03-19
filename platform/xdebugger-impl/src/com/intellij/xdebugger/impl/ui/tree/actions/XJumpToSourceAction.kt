// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl

private class XJumpToSourceAction : XJumpToSourceActionBase() {
  override suspend fun navigateToSource(project: Project, value: XValue): Boolean {
    return navigateByNavigatable(project) { navigatable ->
      value.computeSourcePosition(navigatable)
    }
  }

  override fun isEnabled(node: XValueNodeImpl, e: AnActionEvent): Boolean {
    return super.isEnabled(node, e) && node.valueContainer.canNavigateToSource()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
