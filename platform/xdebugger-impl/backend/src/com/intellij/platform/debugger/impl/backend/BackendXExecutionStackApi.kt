// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.debugger.impl.rpc.*
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.future.await

internal class BackendXExecutionStackApi : XExecutionStackApi {
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
          // Create a copy of stackFrames to avoid concurrent modification
          val framesCopy = stackFrames.toList()

          channel.trySend(this@channelFlow.async {
            val session = executionStackModel.session
            val stackDtos = framesCopy.map { frame ->
              frame.toRpc(executionStackModel.coroutineScope, session)
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
      dropFrameHandler.canDropFrameAsync(stack.stackFrame).await()
    }
  }

  override suspend fun dropFrame(sessionId: XDebugSessionId, stackFrameId: XStackFrameId) {
    val session = sessionId.findValue() ?: return
    val stack = stackFrameId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      session.debugProcess.dropFrameHandler?.drop(stack.stackFrame)
    }
  }

  override suspend fun computeUiPresentation(stackFrameId: XStackFrameId): Flow<XStackFramePresentation> {
    return stackFrameId.findValue()?.stackFrame?.customizePresentation()?.map { presentation ->
      val fragments = buildList {
        presentation.fragments.forEach { (text, attributes) ->
          add(XStackFramePresentationFragment(text, attributes.toRpc()))
        }
      }
      XStackFramePresentation(fragments, presentation.icon?.rpcId(), presentation.tooltipText)
    } ?: emptyFlow()
  }
}
