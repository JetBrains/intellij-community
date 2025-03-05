// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerDocumentOffsetEvaluator
import com.intellij.xdebugger.impl.rhizome.XDebugSessionEntity
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorDto
import com.jetbrains.rhizomedb.entity
import fleet.kernel.rete.collect
import fleet.kernel.rete.query
import fleet.kernel.withEntities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow

internal class BackendXDebugSessionApi : XDebugSessionApi {
  override suspend fun currentEvaluator(sessionId: XDebugSessionId): Flow<XDebuggerEvaluatorDto?> {
    val sessionEntity = entity(XDebugSessionEntity.SessionId, sessionId) ?: return emptyFlow()
    return channelFlow {
      withEntities(sessionEntity) {
        query { sessionEntity.evaluator }.collect { entity ->
          if (entity == null) {
            send(null)
            return@collect
          }
          val canEvaluateInDocument = entity.evaluator is XDebuggerDocumentOffsetEvaluator
          send(XDebuggerEvaluatorDto(entity.evaluatorId, canEvaluateInDocument))
        }
      }
    }
  }
}