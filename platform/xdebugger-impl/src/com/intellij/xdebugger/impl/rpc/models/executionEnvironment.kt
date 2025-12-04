// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.platform.debugger.impl.rpc.ExecutionEnvironmentId
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import kotlinx.coroutines.CoroutineScope

internal fun ExecutionEnvironmentId.findValue(): ExecutionEnvironment? {
  return findValueById(this, type = ExecutionEnvironmentValueIdType)
}

internal fun ExecutionEnvironment.storeGlobally(coroutineScope: CoroutineScope): ExecutionEnvironmentId {
  return storeValueGlobally(coroutineScope, this, type = ExecutionEnvironmentValueIdType)
}

private object ExecutionEnvironmentValueIdType : BackendValueIdType<ExecutionEnvironmentId, ExecutionEnvironment>(::ExecutionEnvironmentId)

