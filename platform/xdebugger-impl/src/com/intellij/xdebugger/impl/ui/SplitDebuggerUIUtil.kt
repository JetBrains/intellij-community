// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.findContentValue
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.frontend.FrontendApplicationInfo.getFrontendType
import com.intellij.frontend.FrontendType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.ExecutionDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.debugger.impl.rpc.XDebuggerTreeSelectedValueId
import com.intellij.platform.debugger.impl.rpc.XExecutionStackId
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.SplitDebuggerMode.showSplitWarnings
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.rpc.ExecutionEnvironmentId
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModel
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeSelectedValue
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SplitDebuggerUIUtil {
  val SPLIT_RUN_CONTENT_DESCRIPTOR_KEY: DataKey<RunContentDescriptorIdImpl> = DataKey.create("SPLIT_RUN_CONTENT_DESCRIPTOR_KEY")
  val SPLIT_EXECUTION_ENVIRONMENT_KEY: DataKey<ExecutionEnvironmentId> = DataKey.create("SPLIT_EXECUTION_ENVIRONMENT_KEY")

  /**
   * This key is used to pass ids of [XValue]s corresponding to the selected nodes in the tree from frontend to backend.
   */
  @JvmField
  internal val SPLIT_SELECTED_VALUES_KEY: DataKey<List<XDebuggerTreeSelectedValueId>> = DataKey.create("split.xdebugger.tree.selected.values")

  /**
   * This key is used to pass ids of [XExecutionStack]s corresponding to the selected stacks in the tree from frontend to backend.
   */
  @JvmField
  internal val SPLIT_SELECTED_STACKS_KEY: DataKey<List<XExecutionStackId>> = DataKey.create("split.xExecutionStack.selected.stacks")

  private val LOG: Logger = Logger.getInstance(SplitDebuggerUIUtil::class.java)

  @JvmStatic
  fun getRunContentDescriptor(dataContext: DataContext): RunContentDescriptor? {
    LangDataKeys.RUN_CONTENT_DESCRIPTOR.getData(dataContext)?.let { return it }
    return SPLIT_RUN_CONTENT_DESCRIPTOR_KEY.getData(dataContext)?.findContentValue()
  }

  @JvmStatic
  fun getExecutionEnvironment(dataContext: DataContext): ExecutionEnvironment? {
    ExecutionDataKeys.EXECUTION_ENVIRONMENT.getData(dataContext)?.let { return it }
    return SPLIT_EXECUTION_ENVIRONMENT_KEY.getData(dataContext)?.findValue()
  }

  @JvmStatic
  fun getXDebuggerTreeSelectedBackendValues(dataContext: DataContext): List<XDebuggerTreeSelectedValue> {
    if (showSplitWarnings() && getFrontendType() is FrontendType.Remote) {
      LOG.error("SplitDebuggerUIUtil#getXDebuggerTreeSelectedBackendValues should not be called from the frontend. " +
                "Please use XDebuggerTree.SELECTED_NODES#getData instead.")
    }
    return if (SplitDebuggerMode.isSplitDebugger()) {
      // In Split mode, find backend values by the ids passed from the frontend
      SPLIT_SELECTED_VALUES_KEY.getData(dataContext)?.mapNotNull { (xValueId, name) ->
        val xValue = BackendXValueModel.findById(xValueId)?.xValue ?: return@mapNotNull null
        XDebuggerTreeSelectedValue(xValue, name)
      } ?: emptyList()
    } else {
      // If Split mode is disabled, SELECTED_NODES contains backend values
      XDebuggerTree.SELECTED_NODES.getData(dataContext)?.map {
        XDebuggerTreeSelectedValue(it.valueContainer, it.name)
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
      SPLIT_SELECTED_STACKS_KEY.getData(dataContext)?.mapNotNull { xExecutionStackId ->
        xExecutionStackId.findValue()?.executionStack
      }
    } else {
      // If Split mode is disabled, SELECTED_STACKS contains backend values
      XExecutionStack.SELECTED_STACKS.getData(dataContext)
    } ?: emptyList()
  }
}
