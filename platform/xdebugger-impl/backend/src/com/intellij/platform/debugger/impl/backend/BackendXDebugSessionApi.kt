// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.BackendDocumentId
import com.intellij.ide.rpc.FrontendDocumentId
import com.intellij.ide.rpc.bindToFrontend
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.rpc.*
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.rpc.models.storeGlobally
import fleet.rpc.core.toRpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

internal class BackendXDebugSessionApi : XDebugSessionApi {
  override suspend fun currentSourcePosition(sessionId: XDebugSessionId): Flow<XSourcePositionDto?> {
    val session = sessionId.findValue() ?: return emptyFlow()
    return session.getCurrentPositionFlow().map { sourcePosition ->
      sourcePosition?.toRpc()
    }
  }

  override suspend fun currentSessionState(sessionId: XDebugSessionId): Flow<XDebugSessionState> {
    val session = sessionId.findValue() ?: return emptyFlow()

    return combine(
      session.isPausedState, session.isStoppedState, session.isReadOnlyState, session.isPauseActionSupportedState, session.isSuspendedState
    ) { isPaused, isStopped, isReadOnly, isPauseActionSupported, isSuspended ->
      XDebugSessionState(isPaused, isStopped, isReadOnly, isPauseActionSupported, isSuspended)
    }
  }

  override suspend fun createDocument(frontendDocumentId: FrontendDocumentId, sessionId: XDebugSessionId, expression: XExpressionDto, sourcePosition: XSourcePositionDto?, evaluationMode: EvaluationMode): BackendDocumentId? {
    val session = sessionId.findValue() ?: return null
    val project = session.project
    val editorsProvider = session.debugProcess.editorsProvider
    return withContext(Dispatchers.EDT) {
      val backendDocument = editorsProvider.createDocument(project, expression.xExpression(), sourcePosition?.sourcePosition(), evaluationMode)
      backendDocument.bindToFrontend(frontendDocumentId)
    }
  }

  override suspend fun sessionTabInfo(sessionId: XDebugSessionId): Flow<XDebuggerSessionTabDto?> {
    val session = sessionId.findValue() ?: return emptyFlow()
    return session.tabInitDataFlow.map {
      if (it == null) return@map null
      XDebuggerSessionTabDto(it, session.getPausedFlow().toRpc())
    }
  }

  override suspend fun resume(sessionId: XDebugSessionId) {
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.resume()
    }
  }

  override suspend fun pause(sessionId: XDebugSessionId) {
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.pause()
    }
  }

  override suspend fun stepOver(sessionId: XDebugSessionId, ignoreBreakpoints: Boolean) {
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.stepOver(ignoreBreakpoints)
    }
  }

  override suspend fun triggerUpdate(sessionId: XDebugSessionId) {
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.rebuildViews()
    }
  }

  override suspend fun updateExecutionPosition(sessionId: XDebugSessionId) {
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.updateExecutionPosition()
    }
  }

  override suspend fun onTabInitialized(sessionId: XDebugSessionId, tabInfo: XDebuggerSessionTabInfoCallback) {
    val tab = tabInfo.tab ?: return
    val session = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.tabInitialized(tab)
    }
  }

  override suspend fun setCurrentStackFrame(sessionId: XDebugSessionId, executionStackId: XExecutionStackId, frameId: XStackFrameId, isTopFrame: Boolean) {
    val session = sessionId.findValue() ?: return
    val executionStackModel = executionStackId.findValue() ?: return
    val stackFrameModel = frameId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.setCurrentStackFrame(executionStackModel.executionStack, stackFrameModel.stackFrame, isTopFrame)
    }
  }

  override suspend fun computeExecutionStacks(suspendContextId: XSuspendContextId): Flow<XExecutionStacksEvent> {
    val suspendContextModel = suspendContextId.findValue() ?: return emptyFlow()
    return channelFlow {
      suspendContextModel.suspendContext.computeExecutionStacks(object : XSuspendContext.XExecutionStackContainer {
        override fun addExecutionStack(executionStacks: List<XExecutionStack>, last: Boolean) {
          val session = suspendContextModel.session
          val stacks = executionStacks.map { stack ->
            val id = stack.storeGlobally(suspendContextModel.coroutineScope, session)
            XExecutionStackDto(id, stack.displayName, stack.icon?.rpcId())
          }
          trySend(XExecutionStacksEvent.NewExecutionStacks(stacks, last))
          if (last) {
            this@channelFlow.close()
          }
        }

        override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
          trySend(XExecutionStacksEvent.ErrorOccurred(errorMessage))
        }
      })
      awaitClose()
    }.buffer(Channel.UNLIMITED)
  }
}

internal fun createXStackFrameDto(frame: XStackFrame, id: XStackFrameId): XStackFrameDto {
  val equalityObject = frame.equalityObject
  val serializedEqualityObject = when (equalityObject) {
    is String -> XStackFrameStringEqualityObject(equalityObject)
    else -> null // TODO support other types
  }
  val canEvaluateInDocument = frame.isDocumentEvaluator
  val evaluatorDto = XDebuggerEvaluatorDto(canEvaluateInDocument)
  return XStackFrameDto(id, frame.sourcePosition?.toRpc(), serializedEqualityObject, evaluatorDto)
}
