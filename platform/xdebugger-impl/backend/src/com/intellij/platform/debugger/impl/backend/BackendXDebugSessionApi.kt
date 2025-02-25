// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.BackendDocumentId
import com.intellij.ide.rpc.FrontendDocumentId
import com.intellij.ide.rpc.bindToFrontend
import com.intellij.openapi.application.EDT
import com.intellij.platform.project.asProject
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerDocumentOffsetEvaluator
import com.intellij.xdebugger.impl.rhizome.XDebugSessionEntity
import com.intellij.xdebugger.impl.rpc.*
import com.jetbrains.rhizomedb.entity
import fleet.kernel.rete.collect
import fleet.kernel.rete.query
import fleet.kernel.withEntities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

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

  override suspend fun currentSourcePosition(sessionId: XDebugSessionId): Flow<XSourcePositionDto?> {
    val sessionEntity = entity(XDebugSessionEntity.SessionId, sessionId) ?: return emptyFlow()
    return channelFlow {
      withEntities(sessionEntity) {
        query { sessionEntity.currentSourcePosition }.collect { sourcePosition ->
          if (sourcePosition == null) {
            send(null)
            return@collect
          }
          send(sourcePosition.toRpc())
        }
      }
    }
  }

  override suspend fun createDocument(frontendDocumentId: FrontendDocumentId, sessionId: XDebugSessionId, expression: XExpressionDto, sourcePosition: XSourcePositionDto?, evaluationMode: EvaluationMode): BackendDocumentId? {
    val sessionEntity = entity(XDebugSessionEntity.SessionId, sessionId) ?: return null
    val project = sessionEntity.projectEntity.asProject()
    val editorsProvider = sessionEntity.session.debugProcess.editorsProvider
    return withContext(Dispatchers.EDT) {
      val backendDocument = editorsProvider.createDocument(project, expression.xExpression(), sourcePosition?.sourcePosition(), evaluationMode)
      backendDocument.bindToFrontend(frontendDocumentId)
    }
  }
}