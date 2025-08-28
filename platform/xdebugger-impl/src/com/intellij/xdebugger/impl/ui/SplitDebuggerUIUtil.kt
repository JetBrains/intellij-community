// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.ExecutionDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.xdebugger.impl.rpc.ExecutionEnvironmentId
import com.intellij.xdebugger.impl.rpc.RunContentDescriptorId
import com.intellij.xdebugger.impl.rpc.models.findValue
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SplitDebuggerUIUtil {
  val SPLIT_RUN_CONTENT_DESCRIPTOR_KEY: DataKey<RunContentDescriptorId> = DataKey.create("SPLIT_RUN_CONTENT_DESCRIPTOR_KEY")
  val SPLIT_EXECUTION_ENVIRONMENT_KEY: DataKey<ExecutionEnvironmentId> = DataKey.create("SPLIT_EXECUTION_ENVIRONMENT_KEY")

  @JvmStatic
  fun getRunContentDescriptor(dataContext: DataContext): RunContentDescriptor? {
    LangDataKeys.RUN_CONTENT_DESCRIPTOR.getData(dataContext)?.let { return it }
    return SPLIT_RUN_CONTENT_DESCRIPTOR_KEY.getData(dataContext)?.findValue()
  }

  @JvmStatic
  fun getExecutionEnvironment(dataContext: DataContext): ExecutionEnvironment? {
    ExecutionDataKeys.EXECUTION_ENVIRONMENT.getData(dataContext)?.let { return it }
    return SPLIT_EXECUTION_ENVIRONMENT_KEY.getData(dataContext)?.findValue()
  }

}
