// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.platform.kernel.backend.asNullableIDsFlow
import com.intellij.platform.kernel.backend.findValueEntity
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

internal class BackendXDebugSessionApi : XDebugSessionApi {
  override suspend fun currentEvaluator(sessionId: XDebugSessionId): Flow<XDebuggerEvaluatorId?> {
    val sessionEntity = sessionId.id.findValueEntity<XDebugSession>() ?: return emptyFlow()
    val session = sessionEntity.value

    // NB!: we assume that the current evaluator depends only on the current StackFrame
    val currentEvaluator = (session as XDebugSessionImpl).currentStackFrameFlow.map { it?.get()?.evaluator }
    return currentEvaluator.asNullableIDsFlow().map { id ->
      id?.let { XDebuggerEvaluatorId(id) }
    }
  }
}