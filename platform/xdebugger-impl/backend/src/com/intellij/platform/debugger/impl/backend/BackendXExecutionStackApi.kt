// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.rhizome.XDebuggerEntity.Companion.debuggerEntity
import com.intellij.xdebugger.impl.rhizome.XDebuggerEntity.Companion.new
import com.intellij.xdebugger.impl.rhizome.XExecutionStackEntity
import com.intellij.xdebugger.impl.rhizome.XStackFrameEntity
import com.intellij.xdebugger.impl.rpc.*
import fleet.kernel.change
import fleet.kernel.withEntities
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
    val entity = debuggerEntity<XExecutionStackEntity>(executionStackId.id) ?: return emptyFlow()
    return channelFlow {
      withEntities(entity) {
        val executionStack = entity.obj
        executionStack.topFrame
      }
    }
  }

  override suspend fun computeStackFrames(executionStackId: XExecutionStackId, firstFrameIndex: Int): Flow<XStackFramesEvent> {
    val executionStackEntity = debuggerEntity<XExecutionStackEntity>(executionStackId.id) ?: return emptyFlow()
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

      // TODO[IJPL-177087]: executionStackEntity gets removed from DB because new ones come to the flow for some reason,
      //  so addStackFrames may end abruptly, with not all stack frames shown.
      //  Investigate whether it's really needed
      withEntities(executionStackEntity) {
        val executionStack = executionStackEntity.obj
        executionStack.computeStackFrames(firstFrameIndex, object : XExecutionStack.XStackFrameContainer {
          override fun addStackFrames(stackFrames: List<XStackFrame>, last: Boolean) {
            channel.trySend(this@channelFlow.async {
              withEntities(executionStackEntity) {
                val frameEntities = stackFrames.map { frame ->
                  change {
                    XStackFrameEntity.new(this, frame)
                  }
                }
                change {
                  executionStackEntity.update {
                    it[XExecutionStackEntity.StackFrames] = executionStackEntity.frames + frameEntities
                  }
                }
                val stacks = frameEntities.map { frame ->
                  XStackFrameDto(XStackFrameId(frame.id), frame.obj.sourcePosition?.toRpc())
                }
                XStackFramesEvent.XNewStackFrames(stacks, last)
              }
            })
          }

          override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
            channel.trySend(this@channelFlow.async {
              XStackFramesEvent.ErrorOccurred(errorMessage)
            })
          }
        })
      }
      awaitClose()
    }
  }
}
