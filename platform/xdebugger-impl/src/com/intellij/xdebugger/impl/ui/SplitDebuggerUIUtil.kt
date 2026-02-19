// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.execution.findContentValue
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.frontend.FrontendApplicationInfo.getFrontendType
import com.intellij.frontend.FrontendType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ExecutionDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.SplitDebuggerMode.showSplitWarnings
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModel
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeSelectedValue
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SplitDebuggerUIUtil {
  private val LOG: Logger = Logger.getInstance(SplitDebuggerUIUtil::class.java)

  @JvmStatic
  fun getRunContentDescriptor(dataContext: DataContext): RunContentDescriptor? {
    LangDataKeys.RUN_CONTENT_DESCRIPTOR.getData(dataContext)?.let { return it }
    return SplitDebuggerDataKeys.SPLIT_RUN_CONTENT_DESCRIPTOR_KEY.getData(dataContext)?.findContentValue()
  }

  @JvmStatic
  fun getExecutionEnvironment(dataContext: DataContext): ExecutionEnvironment? {
    ExecutionDataKeys.EXECUTION_ENVIRONMENT.getData(dataContext)?.let { return it }
    return SplitDebuggerDataKeys.SPLIT_EXECUTION_ENVIRONMENT_KEY.getData(dataContext)?.findValue()
  }

  /**
   * In split mode:
   * * In RemDev, returns backend conterpart of the [com.intellij.platform.debugger.impl.rpc.XValueId] selected on the frontend,
   *    while node is null.
   * * In Monolith, prefers backend [XValue] if existis, or value from the node otherwise. Node is the selected node.
   *
   * Without split mode return the selected nodes without changes of [XValue]s.
   *
   * Should not be called from the frontend.
   */
  @JvmStatic
  fun getXDebuggerTreeSelectedBackendValues(dataContext: DataContext): List<XDebuggerTreeSelectedValue> {
    if (showSplitWarnings() && getFrontendType() is FrontendType.Remote) {
      LOG.error("SplitDebuggerUIUtil#getXDebuggerTreeSelectedBackendValues should not be called from the frontend. " +
                "Please use XDebuggerTreeSplitActionBase#getSelectedNode instead.")
      return emptyList()
    }
    return if (SplitDebuggerMode.isSplitDebugger()) {
      SplitDebuggerDataKeys.SPLIT_SELECTED_VALUES_KEY.getData(dataContext)?.mapNotNull { (xValueId, name, node) ->
        // Find backend values by the ids passed from the frontend
        val splitValue = xValueId?.let { BackendXValueModel.findById(it)?.xValue }
        // If no backend value, use the node's valueContainer.
        // This is important for custom Xvalues created without the platform support,
        // or for test DataContext creation.
        val xValue = splitValue ?: (node as? XValueNodeImpl)?.valueContainer ?: return@mapNotNull null
        XDebuggerTreeSelectedValue(xValue, name, node as? XValueNodeImpl)
      } ?: emptyList()
    } else {
      // If Split mode is disabled, SELECTED_NODES contains backend values
      XDebuggerTree.SELECTED_NODES.getData(dataContext)?.map {
        XDebuggerTreeSelectedValue(it.valueContainer, it.name, it)
      } ?: emptyList()
    }
  }

  @JvmStatic
  fun getSelectedBackendXExecutionStacks(dataContext: DataContext): List<XExecutionStack> {
    if (showSplitWarnings() && getFrontendType() is FrontendType.Remote) {
      LOG.error("SplitDebuggerUIUtil#getSelectedBackendXExecutionStacks should not be called from the frontend. " +
                               "Please use XExecutionStack.SELECTED_STACKS#getData instead.")
    }
    return if (SplitDebuggerMode.isSplitDebugger()) {
      // In Split mode, find backend XExecutionStacks by the ids passed from the frontend
      SplitDebuggerDataKeys.SPLIT_SELECTED_STACKS_KEY.getData(dataContext)?.mapNotNull { xExecutionStackId ->
        xExecutionStackId.findValue()?.executionStack
      }
    } else {
      // If Split mode is disabled, SELECTED_STACKS contains backend values
      XExecutionStack.SELECTED_STACKS.getData(dataContext)
    } ?: emptyList()
  }
}
