// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.xdebugger.frame.XNavigatable
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import org.jetbrains.annotations.ApiStatus

private class XJumpToSourceAction : XJumpToSourceActionBase() {
  override fun startComputingSourcePosition(value: XValue, navigatable: XNavigatable) {
    value.computeSourcePosition(navigatable)
  }

  override fun isEnabled(node: XValueNodeImpl, e: AnActionEvent): Boolean {
    return super.isEnabled(node, e) && node.getValueContainer().canNavigateToSource()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
