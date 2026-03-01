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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.joinAll
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
      val pendingPresentationJobs = mutableListOf<Job>()

      executionStack.computeStackFrames(firstFrameIndex, object : XStackFrameContainerEx {
        override fun addStackFrames(stackFrames: List<XStackFrame>, last: Boolean) {
          addStackFrames(stackFrames, null, last)
        }

        override fun addStackFrames(
          stackFrames: List<XStackFrame>,
          toSelect: XStackFrame?,
          last: Boolean,
        ) {
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
          trySend(XStackFramesEvent.XNewStackFrames(frameDtos, frameToSelectId, last))
          val framesWithIds = frameDtos.zip(framesCopy) { dto, frame -> dto.stackFrameId to frame }
          subscribeToPresentationUpdates(executionStackId, framesWithIds, last)
        }

        private fun ProducerScope<XStackFramesEvent>.subscribeToPresentationUpdates(executionStackId: XExecutionStackId,
                                                                                    framesWithIds: List<Pair<XStackFrameId, XStackFrame>>,
                                                                                    last: Boolean) {
          pendingPresentationJobs.addAll(framesWithIds.map { (id, frame) ->
            launch(CoroutineName("Presentation update for $id")) {
              frame.customizePresentation().collectLatest { presentation ->
                val fragments = buildList {
                  presentation.fragments.forEach { (text, attributes) ->
                    add(XStackFramePresentationFragment(text, attributes.rpcId()))
                  }
                }
                val newPresentation = XStackFramePresentation(fragments, presentation.icon?.rpcId(), presentation.tooltipText)
                this@channelFlow.trySend(XStackFramesEvent.NewPresentation(id, newPresentation))
              }
            }
          })
          if (last) {
            // here I rely on two things:
            // 1. subscribeToPresentationUpdates is always called synchronously, because `addStackFrames` is synchronous
            // 2. XStackFrame.customizePresentation() returns a finite flow, as stated in its doc.
            launch(CoroutineName("computeStackFrames finisher for $executionStackId")) {
              pendingPresentationJobs.joinAll()
              this@channelFlow.close()
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
