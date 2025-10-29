// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XExecutionStackApi : RemoteApi<Unit> {

  suspend fun computeStackFrames(executionStackId: XExecutionStackId, firstFrameIndex: Int): Flow<XStackFramesEvent>

  suspend fun computeVariables(xStackFrameId: XStackFrameId): Flow<XValueComputeChildrenEvent>

  suspend fun canDrop(sessionId: XDebugSessionId, stackFrameId: XStackFrameId): Boolean
  suspend fun dropFrame(sessionId: XDebugSessionId, stackFrameId: XStackFrameId)

  suspend fun computeUiPresentation(stackFrameId: XStackFrameId): Flow<XStackFramePresentation>

  companion object {
    @JvmStatic
    suspend fun getInstance(): XExecutionStackApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XExecutionStackApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
data class FrameNotificationRequest(val sessionId: XDebugSessionId?, val content: String)

@ApiStatus.Internal
@Serializable
sealed interface ShowSessionTabRequest {
  val sessionId: XDebugSessionId
}

@ApiStatus.Internal
@Serializable
data class ShowFramesRequest(override val sessionId: XDebugSessionId) : ShowSessionTabRequest
