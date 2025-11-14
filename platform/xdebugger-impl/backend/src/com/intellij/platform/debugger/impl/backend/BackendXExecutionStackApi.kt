// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.rpc.models.findValue
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class BackendXExecutionStackApi : XExecutionStackApi {
  override suspend fun computeStackFrames(executionStackId: XExecutionStackId, firstFrameIndex: Int): Flow<XStackFramesEvent> {
    val executionStackModel = executionStackId.findValue() ?: return emptyFlow()
    return channelFlow {

      val executionStack = executionStackModel.executionStack
      executionStack.computeStackFrames(firstFrameIndex, object : XExecutionStack.XStackFrameContainer {
        override fun addStackFrames(stackFrames: List<XStackFrame>, last: Boolean) {
          // Create a copy of stackFrames to avoid concurrent modification
          val framesCopy = stackFrames.toList()

          val session = executionStackModel.session
          val frameDtos = framesCopy.map { frame ->
            frame.toRpc(executionStackModel.coroutineScope, session)
          }
          trySend(XStackFramesEvent.XNewStackFrames(frameDtos, last))
          val framesWithIds = frameDtos.zip(framesCopy) { dto, frame -> dto.stackFrameId to frame }
          subscribeToPresentationUpdates(framesWithIds)
        }

        private fun ProducerScope<XStackFramesEvent>.subscribeToPresentationUpdates(stacksWithIds: List<Pair<XStackFrameId, XStackFrame>>) {
          for ((id, stack) in stacksWithIds) {
            launch(CoroutineName("Presentation update for $id")) {
              stack.customizePresentation().collectLatest { presentation ->
                val fragments = buildList {
                  presentation.fragments.forEach { (text, attributes) ->
                    add(XStackFramePresentationFragment(text, attributes.toRpc()))
                  }
                }
                val newPresentation = XStackFramePresentation(fragments, presentation.icon?.rpcId(), presentation.tooltipText)
                send(XStackFramesEvent.NewPresentation(id, newPresentation))
              }
            }
          }
        }

        override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
          trySend(XStackFramesEvent.ErrorOccurred(errorMessage))
        }
      })
      awaitClose()
    }.buffer(Channel.UNLIMITED)
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
}
