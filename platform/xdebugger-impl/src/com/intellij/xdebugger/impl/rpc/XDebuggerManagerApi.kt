// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.openapi.editor.impl.EditorId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.RpcFlow
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XDebuggerManagerApi : RemoteApi<Unit> {
  suspend fun currentSession(projectId: ProjectId): Flow<XDebugSessionId?>

  suspend fun sessions(projectId: ProjectId): XDebugSessionsList

  suspend fun reshowInlays(projectId: ProjectId, editorId: EditorId?)

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebuggerManagerApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XDebuggerManagerApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
sealed interface XDebuggerManagerSessionEvent {
  @Serializable
  data class ProcessStarted(val sessionId: XDebugSessionId, val sessionDto: XDebugSessionDto) : XDebuggerManagerSessionEvent

  @Serializable
  data class ProcessStopped(val sessionId: XDebugSessionId) : XDebuggerManagerSessionEvent

  @Serializable
  data class CurrentSessionChanged(val previousSession: XDebugSessionId?, val currentSession: XDebugSessionId?) : XDebuggerManagerSessionEvent
}

@ApiStatus.Internal
@Serializable
sealed interface XDebuggerSessionEvent {
  @Serializable
  class SessionPaused() : XDebuggerSessionEvent

  @Serializable
  class SessionResumed() : XDebuggerSessionEvent

  @Serializable
  class SessionStopped() : XDebuggerSessionEvent

  @Serializable
  class StackFrameChanged() : XDebuggerSessionEvent

  @Serializable
  class BeforeSessionResume() : XDebuggerSessionEvent

  @Serializable
  class SettingsChanged() : XDebuggerSessionEvent

  @Serializable
  class BreakpointsMuted(val muted: Boolean) : XDebuggerSessionEvent
}

@ApiStatus.Internal
@Serializable
data class XDebugSessionsList(
  val list: List<XDebugSessionDto>,
  val eventFlow: RpcFlow<XDebuggerManagerSessionEvent>,
)
