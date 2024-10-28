// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.platform.kernel.backend.cascadeDeleteBy
import com.intellij.platform.kernel.backend.delete
import com.intellij.platform.kernel.backend.findValueEntity
import com.intellij.platform.kernel.backend.registerValueEntity
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow

internal class BackendXDebugSessionApi : XDebugSessionApi {
  override suspend fun currentEvaluator(sessionId: XDebugSessionId): Flow<XDebuggerEvaluatorId?> {
    val sessionEntity = sessionId.id.findValueEntity<XDebugSession>() ?: return emptyFlow()
    val session = sessionEntity.value

    return channelFlow {
      // NB!: we assume that the current evaluator depends only on the current StackFrame
      (session as XDebugSessionImpl).currentStackFrameFlow?.collectLatest { currentStackFrameRef ->
        val evaluator = currentStackFrameRef?.get()?.evaluator
        if (evaluator == null) {
          send(null)
          return@collectLatest
        }
        val evaluatorEntity = registerValueEntity(evaluator).apply {
          cascadeDeleteBy(sessionEntity)
        }
        send(XDebuggerEvaluatorId(evaluatorEntity.id))
        try {
          awaitCancellation()
        }
        catch (_: CancellationException) {
          evaluatorEntity.delete()
        }
      }
    }
  }
}