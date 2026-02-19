// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.platform.debugger.impl.rpc.ExecutionEnvironmentId
import com.intellij.platform.debugger.impl.rpc.XDebuggerTreeSelectedValueId
import com.intellij.platform.debugger.impl.rpc.XExecutionStackId
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XValue
import org.jetbrains.annotations.ApiStatus

/**
 * Data keys for split debugger mode communication between frontend and backend.
 */
@ApiStatus.Internal
object SplitDebuggerDataKeys {
  val SPLIT_RUN_CONTENT_DESCRIPTOR_KEY: DataKey<RunContentDescriptorIdImpl> = DataKey.create("SPLIT_RUN_CONTENT_DESCRIPTOR_KEY")
  val SPLIT_EXECUTION_ENVIRONMENT_KEY: DataKey<ExecutionEnvironmentId> = DataKey.create("SPLIT_EXECUTION_ENVIRONMENT_KEY")

  /**
   * This key is used to pass ids of [XValue]s corresponding to the selected nodes in the tree from frontend to backend.
   */
  @JvmField
  val SPLIT_SELECTED_VALUES_KEY: DataKey<List<XDebuggerTreeSelectedValueId>> = DataKey.create("split.xdebugger.tree.selected.values")

  /**
   * This key is used to pass ids of [XExecutionStack]s corresponding to the selected stacks in the tree from frontend to backend.
   */
  @JvmField
  val SPLIT_SELECTED_STACKS_KEY: DataKey<List<XExecutionStackId>> = DataKey.create("split.xExecutionStack.selected.stacks")
}
