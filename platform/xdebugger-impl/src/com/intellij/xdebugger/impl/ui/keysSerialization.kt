// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.platform.debugger.impl.rpc.ExecutionEnvironmentId
import com.intellij.platform.debugger.impl.rpc.XDebuggerTreeSelectedValueId
import com.intellij.platform.debugger.impl.rpc.XExecutionStackId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

internal class RunContentDescriptorKeySerializer : CustomDataContextSerializer<RunContentDescriptorIdImpl> {
  override val key: DataKey<RunContentDescriptorIdImpl>
    get() = SplitDebuggerDataKeys.SPLIT_RUN_CONTENT_DESCRIPTOR_KEY
  override val serializer: KSerializer<RunContentDescriptorIdImpl>
    get() = RunContentDescriptorIdImpl.serializer()
}

internal class ExecutionEnvironmentKeySerializer : CustomDataContextSerializer<ExecutionEnvironmentId> {
  override val key: DataKey<ExecutionEnvironmentId>
    get() = SplitDebuggerDataKeys.SPLIT_EXECUTION_ENVIRONMENT_KEY
  override val serializer: KSerializer<ExecutionEnvironmentId>
    get() = ExecutionEnvironmentId.serializer()
}

internal class XDebuggerTreeSelectedValuesSerializer : CustomDataContextSerializer<List<XDebuggerTreeSelectedValueId>> {
  override val key: DataKey<List<XDebuggerTreeSelectedValueId>>
    get() = SplitDebuggerDataKeys.SPLIT_SELECTED_VALUES_KEY
  override val serializer: KSerializer<List<XDebuggerTreeSelectedValueId>>
    get() = ListSerializer(XDebuggerTreeSelectedValueId.serializer())
}

internal class XDebuggerTreeSelectedStacksSerializer : CustomDataContextSerializer<List<XExecutionStackId>> {
  override val key: DataKey<List<XExecutionStackId>>
    get() = SplitDebuggerDataKeys.SPLIT_SELECTED_STACKS_KEY
  override val serializer: KSerializer<List<XExecutionStackId>>
    get() = ListSerializer(XExecutionStackId.serializer())
}