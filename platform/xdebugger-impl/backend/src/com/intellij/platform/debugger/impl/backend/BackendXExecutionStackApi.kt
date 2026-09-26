// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.debugger.impl.rpc.ComputeFramesConfig
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.platform.debugger.impl.rpc.XExecutionStackApi
import com.intellij.platform.debugger.impl.rpc.XExecutionStackId
import com.intellij.platform.debugger.impl.rpc.XStackFrameId
import com.intellij.platform.debugger.impl.rpc.XStackFramePresentation
import com.intellij.platform.debugger.impl.rpc.XStackFramePresentationFragment
import com.intellij.platform.debugger.impl.rpc.XStackFramesEvent
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.frame.XStackFrameContainerEx
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class BackendXExecutionStackApi : XExecutionStackApi {
  override suspend fun computeStackFrames(executionStackId: XExecutionStackId, firstFrameIndex: Int, config: ComputeFramesConfig?): Flow<XStackFramesEvent> {
    if (config != null) {
      XDebuggerSettingManagerImpl.getInstanceImpl().dataViewSettings.isShowLibraryStackFrames = config.includeLibraryFrames
    }
    val executionStackModel = executionStackId.findValue() ?: return emptyFlow()
    return channelFlow {
      val executionStack = executionStackModel.executionStack
      val events = Channel<suspend () -> Unit>(Channel.UNLIMITED)

      executionStack.computeStackFrames(firstFrameIndex, object : XStackFrameContainerEx {
        override fun addStackFrames(stackFrames: List<XStackFrame>, last: Boolean) {
          addStackFrames(stackFrames, null, last)
        }

        override fun addStackFrames(
          stackFrames: List<XStackFrame>,
          toSelect: XStackFrame?,
          last: Boolean,
        ) {
          events.trySend {
            // Create a copy of stackFrames to avoid concurrent modification
            val framesCopy = stackFrames.toList()

            val session = executionStackModel.session
            val frameDtos = framesCopy.map { frame ->
              frame.toRpc(executionStackModel.coroutineScope, session)
            }
            val frameToSelectId = toSelect?.let {
              val index = framesCopy.indexOf(it)
              if (index >= 0) frameDtos[index].stackFrameId else null
            }
            send(XStackFramesEvent.XNewStackFrames(frameDtos, frameToSelectId, last))
            frameDtos.zip(framesCopy).forEach { (dto, frame) ->
              subscribeToPresentationUpdates(dto.stackFrameId, frame)
            }
          }
          if (last) {
            // channelFlow waits for its child presentation coroutines before completing.
            events.close()
          }
        }

        private fun subscribeToPresentationUpdates(id: XStackFrameId, frame: XStackFrame) {
          launch(CoroutineName("Presentation update for $id")) {
            // returns a finite flow, as stated in its doc
            frame.customizePresentation().collectLatest { presentation ->
              val fragments = buildList {
                presentation.fragments.forEach { (text, attributes) ->
                  add(XStackFramePresentationFragment(text, attributes.rpcId()))
                }
              }
              val newPresentation = XStackFramePresentation(fragments, presentation.icon?.rpcId(), presentation.tooltipText)
              send(XStackFramesEvent.NewPresentation(id, newPresentation))
            }
          }
        }

        override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
          events.trySend { XStackFramesEvent.ErrorOccurred(errorMessage) }
        }
      })

      for (eventComputation in events) {
        eventComputation()
      }
    }.buffer(Channel.BUFFERED)
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
