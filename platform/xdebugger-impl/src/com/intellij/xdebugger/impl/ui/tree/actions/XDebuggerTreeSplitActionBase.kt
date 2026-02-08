// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.platform.debugger.impl.shared.SplitDebuggerAction
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeSplitActionBase.Companion.getSelectedNodes
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import org.jetbrains.annotations.ApiStatus

/**
 * Base class for actions that operate on [XValueNodeImpl] tree nodes.
 *
 * Use this class if the logic of the action is split and needs to operate on the frontend.
 *
 * This action uses frontend node instances ([XValueNodeImpl]) which are only available on the frontend. 
 * Moreover, for nodes used by this action [XValueContainerNode.getValueContainer] returns frontend-specific [XValue]s,
 * which cannot be cast to an [XValue] obtained from plugin-specific [XDebugProcess].
 * 
 * For backend actions, use [XDebuggerTreeBackendOnlyActionBase] instead, which works with backend [XValue] instances.
 */
@ApiStatus.Experimental
abstract class XDebuggerTreeSplitActionBase : AnAction(), SplitDebuggerAction {
  override fun actionPerformed(e: AnActionEvent) {
    val node = getSelectedNode(e.dataContext)
    if (node != null) {
      val nodeName = node.name
      if (nodeName != null) {
        perform(node, nodeName, e)
      }
    }
  }

  protected abstract fun perform(node: XValueNodeImpl, nodeName: String, e: AnActionEvent)

  override fun update(e: AnActionEvent) {
    val node = getSelectedNode(e.dataContext)
    e.presentation.setEnabled(node != null && isEnabled(node, e))
  }

  protected open fun isEnabled(node: XValueNodeImpl, e: AnActionEvent): Boolean {
    return node.name != null
  }

  companion object {

    /**
     * Returns the list of [XValueNodeImpl]s corresponding to the selected nodes in the tree.
     * For the resulting nodes [XValueContainerNode.getValueContainer] returns frontend-specific [XValue]s.
     */
    @JvmStatic
    fun getSelectedNodes(dataContext: DataContext): List<XValueNodeImpl> {
      return XDebuggerTree.getSelectedNodes(dataContext)
    }

    /**
     * Returns the first of the selected nodes returned by [getSelectedNodes] or null if no nodes were selected.
     */
    @JvmStatic
    fun getSelectedNode(dataContext: DataContext): XValueNodeImpl? =
      getSelectedNodes(dataContext).firstOrNull()

    /**
     * Returns the frontend-specific [XValue] corresponding to the selected node.
     */
    @JvmStatic
    fun getSelectedValue(dataContext: DataContext): XValue? =
      getSelectedNode(dataContext)?.valueContainer
  }
}