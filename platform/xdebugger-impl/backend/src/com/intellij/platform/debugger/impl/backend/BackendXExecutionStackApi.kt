// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.debugger.impl.rpc.XExecutionStackApi
import com.intellij.platform.debugger.impl.rpc.XStackFrameDto
import com.intellij.platform.debugger.impl.rpc.XStackFramesEvent
import com.intellij.platform.debugger.impl.rpc.XValueComputeChildrenEvent
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XExecutionStackId
import com.intellij.xdebugger.impl.rpc.XStackFrameId
import com.intellij.xdebugger.impl.rpc.models.findValue
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow

internal class BackendXExecutionStackApi : XExecutionStackApi {
  override suspend fun getTopFrame(executionStackId: XExecutionStackId): Flow<XStackFrameDto> {
    val executionStackModel = executionStackId.findValue() ?: return emptyFlow()
    return channelFlow {
      // TODO: send dto!!
      executionStackModel.executionStack.topFrame
    }
  }

  override suspend fun computeStackFrames(executionStackId: XExecutionStackId, firstFrameIndex: Int): Flow<XStackFramesEvent> {
    val executionStackModel = executionStackId.findValue() ?: return emptyFlow()
    return channelFlow {
      val channel = Channel<Deferred<XStackFramesEvent>>(capacity = Channel.UNLIMITED)

      launch {
        for (event in channel) {
          val event = event.await()
          this@channelFlow.send(event)
          if (event is XStackFramesEvent.ErrorOccurred || event is XStackFramesEvent.XNewStackFrames && event.last) {
            channel.close()
            this@channelFlow.close()
            break
          }
        }
      }
      val executionStack = executionStackModel.executionStack
      executionStack.computeStackFrames(firstFrameIndex, object : XExecutionStack.XStackFrameContainer {
        override fun addStackFrames(stackFrames: List<XStackFrame>, last: Boolean) {
          channel.trySend(this@channelFlow.async {
            val session = executionStackModel.session
            val stackDtos = stackFrames.map { frame ->
              createXStackFrameDto(frame, executionStackModel.coroutineScope, session)
            }
            XStackFramesEvent.XNewStackFrames(stackDtos, last)
          })
        }

        override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
          channel.trySend(this@channelFlow.async {
            XStackFramesEvent.ErrorOccurred(errorMessage)
          })
        }
      })
      awaitClose()
    }
  }

  override suspend fun computeVariables(xStackFrameId: XStackFrameId): Flow<XValueComputeChildrenEvent> {
    val stackFrameModel = xStackFrameId.findValue() ?: return emptyFlow()
    return computeContainerChildren(stackFrameModel.coroutineScope, stackFrameModel.stackFrame, stackFrameModel.session)
  }

  override suspend fun canDrop(sessionId: XDebugSessionId, stackFrameId: XStackFrameId): Boolean {
    val session = sessionId.findValue() ?: return false
    val stack = stackFrameId.findValue() ?: return false
    return withContext(Dispatchers.EDT) {
      val dropFrameHandler = session.debugProcess.dropFrameHandler ?: return@withContext false
      dropFrameHandler.canDrop(stack.stackFrame)
    }
  }

  override suspend fun dropFrame(sessionId: XDebugSessionId, stackFrameId: XStackFrameId) {
    val session = sessionId.findValue() ?: return
    val stack = stackFrameId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.debugProcess.dropFrameHandler?.drop(stack.stackFrame)
    }
  }
}
