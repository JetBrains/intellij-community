// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions

import com.intellij.idea.AppMode
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.diagnostic.Logger
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.SplitDebuggerMode.showSplitWarnings
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.ui.SplitDebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase.Companion.getSelectedNodes
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImplDelegate

/**
 * Base class for actions that operate on frontend [XValueNodeImpl] tree nodes
 * but still need access to backend [XValue] instances.
 *
 * Note: This class is supposed to maintain backward compatibility for plugins during the migration to Split Mode.
 * It bridges the gap by providing [XValueNodeImpl] nodes that expose backend [XValue]s obtained from plugin-specific [XDebugProcess].
 *
 * In Monolith Mode: the action works as it did before Split mode, providing nodes with backend [XValue]s.
 * In Remote Development: the action DOES NOT work. Since [XValueNodeImpl] is a frontend entity,
 * it cannot be accessed from the backend.
 *
 * For Rem-Dev mode:
 * - For a backend action, which only operates on the backend [XValue]s and
 * does not need access to frontend [XValueNodeImpl] nodes, use [XDebuggerTreeBackendOnlyActionBase].
 * - For an action which operates on the frontend with the already split logic use [XDebuggerTreeSplitActionBase].
 */
abstract class XDebuggerTreeActionBase : AnAction(), ActionRemoteBehaviorSpecification.BackendOnly {
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
     * Returns the list of [XValueNodeImpl]s corresponding to the selected nodes in the tree and
     * each node exposes a backend [XValue].
     *
     * This method only works in Monolith, since [XValueNodeImpl] is a frontend UI entity and
     * cannot be accessed from the backend.
     */
    @JvmStatic
    fun getSelectedNodes(dataContext: DataContext): List<XValueNodeImpl> {
      if (!SplitDebuggerMode.isSplitDebugger()) {
        return XDebuggerTree.SELECTED_NODES.getData(dataContext) ?: emptyList()
      }

      if (showSplitWarnings() && AppMode.isRemoteDevHost()) {
        LOG.error("""
          XDebuggerTreeActionBase should not be used in Rem-Dev mode:
          - for backend only actions use XDebuggerTreeBackendOnlyActionBase
          - for actions that operate on the frontend use XDebuggerTreeSplitActionBase
        """)
        return emptyList()
      }

      return fetchSelectedValues(dataContext).mapNotNull { (backendValue, _, node) ->
        if (node == null) return@mapNotNull null
        // no need to wrap if the node already exposes the backend value
        if (node.valueContainer === backendValue) return@mapNotNull node
        // replace the node with a delegate that exposes the backend value
        object : XValueNodeImplDelegate(node, backendValue) {
          override fun getValueContainer(): XValue {
            return backendValue
          }
        }
      }
    }


    /**
     * Returns the first of the selected nodes returned by [getSelectedNodes] or null if no nodes were selected.
     *
     * This method only works in Monolith, since [XValueNodeImpl] is a frontend UI entity and
     * cannot be accessed from the backend.
     */
    @JvmStatic
    fun getSelectedNode(dataContext: DataContext): XValueNodeImpl? =
      getSelectedNodes(dataContext).firstOrNull()

    /**
     * Returns the backend [XValue] corresponding to the selected node.
     *
     * This method works in both Monolith and Rem-Dev,
     * though for a backend action which only operates on the backend [XValue]s, consider using [XDebuggerTreeBackendOnlyActionBase]
     * instead of calling this method directly.
     */
    @JvmStatic
    fun getSelectedValue(dataContext: DataContext): XValue? =
      fetchSelectedValues(dataContext).firstOrNull()?.xValue

    private fun fetchSelectedValues(dataContext: DataContext): List<XDebuggerTreeSelectedValue> {
      val splitValues = SplitDebuggerUIUtil.getXDebuggerTreeSelectedBackendValues(dataContext)
      return splitValues + fetchSelectedNodeValues(splitValues, dataContext)
    }

    /**
     * Nodes could be added into context manually (e.g. from tests)
     */
    private fun fetchSelectedNodeValues(
      selectedSplitValues: List<XDebuggerTreeSelectedValue>,
      dataContext: DataContext,
    ): List<XDebuggerTreeSelectedValue> {
      if (!SplitDebuggerMode.isSplitDebugger()) return emptyList()
      val selectedNodes = XDebuggerTree.SELECTED_NODES.getData(dataContext) ?: return emptyList()

      val splitValueNodes = selectedSplitValues.map { it.node }
      return selectedNodes
        .filter { it !in splitValueNodes }
        .map { XDebuggerTreeSelectedValue(it.valueContainer, it.name, it) }
    }

    private val LOG = Logger.getInstance(XDebuggerTreeActionBase::class.java)
  }
}