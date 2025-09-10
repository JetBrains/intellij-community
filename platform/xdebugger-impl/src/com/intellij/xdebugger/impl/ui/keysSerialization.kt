// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.xdebugger.impl.rpc.ExecutionEnvironmentId
import com.intellij.xdebugger.impl.rpc.RunContentDescriptorId
import kotlinx.serialization.KSerializer

private class RunContentDescriptorKeySerializer : CustomDataContextSerializer<RunContentDescriptorId> {
  override val key: DataKey<RunContentDescriptorId>
    get() = SplitDebuggerUIUtil.SPLIT_RUN_CONTENT_DESCRIPTOR_KEY
  override val serializer: KSerializer<RunContentDescriptorId>
    get() = RunContentDescriptorId.serializer()
}

private class ExecutionEnvironmentKeySerializer : CustomDataContextSerializer<ExecutionEnvironmentId> {
  override val key: DataKey<ExecutionEnvironmentId>
    get() = SplitDebuggerUIUtil.SPLIT_EXECUTION_ENVIRONMENT_KEY
  override val serializer: KSerializer<ExecutionEnvironmentId>
    get() = ExecutionEnvironmentId.serializer()
}
