// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.rpc.XExecutionStackApi
import com.intellij.xdebugger.impl.rpc.XExecutionStackId
import com.intellij.xdebugger.impl.rpc.XStackFrameDto
import com.intellij.xdebugger.impl.rpc.XStackFrameId
import com.intellij.xdebugger.impl.rpc.XStackFramesEvent
import com.intellij.xdebugger.impl.rpc.XValueComputeChildrenEvent
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.rpc.models.getOrStoreGlobally
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

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
              val stackFrameId = frame.getOrStoreGlobally(executionStackModel.coroutineScope, session)
              createXStackFrameDto(frame, stackFrameId)
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
}
