// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.BackendDocumentId
import com.intellij.ide.rpc.FrontendDocumentId
import com.intellij.ide.rpc.bindToFrontend
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.project.asProject
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XSteppingSuspendContext
import com.intellij.xdebugger.impl.rhizome.*
import com.intellij.xdebugger.impl.rhizome.XDebuggerEntity.Companion.debuggerEntity
import com.intellij.xdebugger.impl.rhizome.XDebuggerEntity.Companion.new
import com.intellij.xdebugger.impl.rpc.*
import com.jetbrains.rhizomedb.entity
import fleet.kernel.change
import fleet.kernel.rete.collect
import fleet.kernel.rete.query
import fleet.kernel.withEntities
import fleet.rpc.core.toRpc
import fleet.util.UID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

internal class BackendXDebugSessionApi : XDebugSessionApi {
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

  override suspend fun currentSessionState(sessionId: XDebugSessionId): Flow<XDebugSessionState> {
    val sessionEntity = entity(XDebugSessionEntity.SessionId, sessionId) ?: return emptyFlow()
    val session = sessionEntity.session as XDebugSessionImpl

    return combine(
      session.isPausedState, session.isStoppedState, session.isReadOnlyState, session.isPauseActionSupportedState, session.isSuspendedState
    ) { isPaused, isStopped, isReadOnly, isPauseActionSupported, isSuspended ->
      XDebugSessionState(isPaused, isStopped, isReadOnly, isPauseActionSupported, isSuspended)
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

  override suspend fun sessionTabInfo(sessionId: XDebugSessionId): Flow<XDebuggerSessionTabDto?> {
    val sessionEntity = entity(XDebugSessionEntity.SessionId, sessionId) ?: return emptyFlow()
    val session = sessionEntity.session as? XDebugSessionImpl ?: return emptyFlow()
    return session.tabInitDataFlow.map {
      if (it == null) return@map null
      XDebuggerSessionTabDto(it, session.getPausedFlow().toRpc())
    }
  }

  override suspend fun resume(sessionId: XDebugSessionId) {
    val session = entity(XDebugSessionEntity.SessionId, sessionId)?.session ?: return
    withContext(Dispatchers.EDT) {
      session.resume()
    }
  }

  override suspend fun pause(sessionId: XDebugSessionId) {
    val session = entity(XDebugSessionEntity.SessionId, sessionId)?.session ?: return
    withContext(Dispatchers.EDT) {
      session.pause()
    }
  }

  override suspend fun stepOver(sessionId: XDebugSessionId, ignoreBreakpoints: Boolean) {
    val session = entity(XDebugSessionEntity.SessionId, sessionId)?.session ?: return
    withContext(Dispatchers.EDT) {
      session.stepOver(ignoreBreakpoints)
    }
  }

  override suspend fun triggerUpdate(sessionId: XDebugSessionId) {
    val session = entity(XDebugSessionEntity.SessionId, sessionId)?.session ?: return
    withContext(Dispatchers.EDT) {
      session.rebuildViews()
    }
  }

  override suspend fun updateExecutionPosition(sessionId: XDebugSessionId) {
    val session = entity(XDebugSessionEntity.SessionId, sessionId)?.session ?: return
    withContext(Dispatchers.EDT) {
      session.updateExecutionPosition()
    }
  }

  override suspend fun onTabInitialized(sessionId: XDebugSessionId, tabInfo: XDebuggerSessionTabInfoCallback) {
    val tab = tabInfo.tab ?: return
    val session = entity(XDebugSessionEntity.SessionId, sessionId)?.session as? XDebugSessionImpl ?: return
    withContext(Dispatchers.EDT) {
      session.tabInitialized(tab)
    }
  }

  override suspend fun currentSuspendContext(sessionId: XDebugSessionId): Flow<XSuspendContextDto?> {
    val sessionEntity = entity(XDebugSessionEntity.SessionId, sessionId) ?: return emptyFlow()
    val session = sessionEntity.session as? XDebugSessionImpl ?: return emptyFlow()
    return channelFlow {
      withEntities(sessionEntity) {
        session.getCurrentSuspendContextFlow().asEntityFlow { suspendContext ->
          XSuspendContextEntity.new(this, suspendContext, sessionEntity)
        }.collect { contextAndId ->
          if (contextAndId == null) {
            send(null)
            return@collect
          }
          val (suspendContext, id) = contextAndId
          send(XSuspendContextDto(XSuspendContextId(id), suspendContext is XSteppingSuspendContext))
        }
      }
    }
  }

  override suspend fun currentExecutionStack(sessionId: XDebugSessionId): Flow<XExecutionStackDto?> {
    val sessionEntity = entity(XDebugSessionEntity.SessionId, sessionId) ?: return emptyFlow()
    val session = sessionEntity.session as? XDebugSessionImpl ?: return emptyFlow()
    return channelFlow {
      withEntities(sessionEntity) {
        session.getCurrentExecutionStackFlow().asEntityFlow { executionStack ->
          XExecutionStackEntity.new(this, executionStack, sessionEntity)
        }.collect { stackAndId ->
          val (executionStack, id) = stackAndId ?: run {
            send(null)
            return@collect
          }
          send(XExecutionStackDto(XExecutionStackId(id), executionStack.displayName, executionStack.icon?.rpcId()))
        }
      }
    }
  }

  override suspend fun currentStackFrame(sessionId: XDebugSessionId): Flow<XStackFrameDto?> {
    val sessionEntity = entity(XDebugSessionEntity.SessionId, sessionId) ?: return emptyFlow()
    val session = sessionEntity.session as? XDebugSessionImpl ?: return emptyFlow()
    return channelFlow {
      withEntities(sessionEntity) {
        session.getCurrentStackFrameFlow().asEntityFlow { stackFrame ->
          XStackFrameEntity.new(this, stackFrame, sessionEntity)
        }.collect { frameAndId ->
          if (frameAndId == null) {
            send(null)
            return@collect
          }
          val (frame, id) = frameAndId
          send(createXStackFrameDto(frame, id))
        }
      }
    }
  }

  override suspend fun setCurrentStackFrame(sessionId: XDebugSessionId, executionStackId: XExecutionStackId, frameId: XStackFrameId, isTopFrame: Boolean) {
    val sessionEntity = entity(XDebugSessionEntity.SessionId, sessionId) ?: return
    val executionStackEntity = debuggerEntity<XExecutionStackEntity>(executionStackId.id) ?: return
    val stackFrameEntity = debuggerEntity<XStackFrameEntity>(frameId.id) ?: return
    withEntities(sessionEntity, executionStackEntity, stackFrameEntity) {
      withContext(Dispatchers.EDT) {
        sessionEntity.session.setCurrentStackFrame(executionStackEntity.obj, stackFrameEntity.obj, isTopFrame)
      }
    }
  }

  override suspend fun computeExecutionStacks(suspendContextId: XSuspendContextId): Flow<XExecutionStacksEvent> {
    val suspendContextEntity = debuggerEntity<XSuspendContextEntity>(suspendContextId.id) as? XSuspendContextEntity ?: return emptyFlow()
    return channelFlow {
      val channel = Channel<Deferred<XExecutionStacksEvent>?>(capacity = Channel.UNLIMITED)

      launch {
        for (event in channel) {
          if (event == null) {
            channel.close()
            this@channelFlow.close()
            break
          }
          send(event.await())
        }
      }

      withEntities(suspendContextEntity) {
        suspendContextEntity.obj.computeExecutionStacks(object : XSuspendContext.XExecutionStackContainer {
          override fun addExecutionStack(executionStacks: List<XExecutionStack>, last: Boolean) {
            channel.trySend(this@channelFlow.async {
              withEntities(suspendContextEntity, suspendContextEntity.sessionEntity) {
                val stackEntities = executionStacks.map { stack ->
                  change {
                    XExecutionStackEntity.new(this, stack, suspendContextEntity.sessionEntity)
                  }
                }
                change {
                  suspendContextEntity.update {
                    it[XSuspendContextEntity.ExecutionStacks] = suspendContextEntity.executionStacks + stackEntities
                  }
                }
                val stacks = stackEntities.map { stack ->
                  withEntities(stack) {
                    XExecutionStackDto(XExecutionStackId(stack.id), stack.obj.displayName, stack.obj.icon?.rpcId())
                  }
                }
                XExecutionStacksEvent.NewExecutionStacks(stacks, last)
              }
            })
            if (last) {
              channel.trySend(null)
            }
          }

          override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
            channel.trySend(this@channelFlow.async {
              XExecutionStacksEvent.ErrorOccurred(errorMessage)
            })
          }
        })
      }
      awaitClose()
    }
  }
}

internal fun createXStackFrameDto(frame: XStackFrame, id: UID): XStackFrameDto {
  val equalityObject = frame.equalityObject
  val serializedEqualityObject = when (equalityObject) {
    is String -> XStackFrameStringEqualityObject(equalityObject)
    else -> null // TODO support other types
  }
  val canEvaluateInDocument = frame.isDocumentEvaluator
  val evaluatorDto = XDebuggerEvaluatorDto(canEvaluateInDocument)
  return XStackFrameDto(XStackFrameId(id), frame.sourcePosition?.toRpc(), serializedEqualityObject, evaluatorDto)
}
