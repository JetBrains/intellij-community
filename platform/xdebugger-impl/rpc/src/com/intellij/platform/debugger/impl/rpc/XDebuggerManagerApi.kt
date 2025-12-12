// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.openapi.editor.impl.EditorId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.UID
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.RpcFlow
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XDebuggerManagerApi : RemoteApi<Unit> {
  suspend fun initialize(projectId: ProjectId, capabilities: XFrontendDebuggerCapabilities)

  suspend fun sessions(projectId: ProjectId): XDebugSessionsList

  suspend fun reshowInlays(projectId: ProjectId, editorId: EditorId?)

  suspend fun getBreakpoints(projectId: ProjectId): XBreakpointsSetDto

  suspend fun sessionTabSelected(projectId: ProjectId, sessionId: XDebugSessionId?)

  suspend fun sessionTabClosed(sessionId: XDebugSessionId)

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebuggerManagerApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XDebuggerManagerApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
data class XFrontendDebuggerCapabilities(
  val canShowImages: Boolean,
)

/**
 * @see XDebugSessionId.findValue
 * @see com.intellij.xdebugger.impl.XDebugSessionImpl.id
 */
@ApiStatus.Internal
@Serializable
data class XDebugSessionId(override val uid: UID) : Id

@ApiStatus.Internal
@Serializable
data class XBreakpointsSetDto(
  val initialBreakpoints: Set<XBreakpointDto>,
  val breakpointEvents: RpcFlow<XBreakpointEvent>,
)

@ApiStatus.Internal
@Serializable
sealed interface XBreakpointEvent {
  @Serializable
  data class BreakpointAdded(val breakpointDto: XBreakpointDto) : XBreakpointEvent

  @Serializable
  data class BreakpointRemoved(val breakpointId: XBreakpointId) : XBreakpointEvent
}

@ApiStatus.Internal
@Serializable
sealed interface XDebuggerManagerSessionEvent {
  @Serializable
  data class ProcessStarted(val sessionDto: XDebugSessionDto) : XDebuggerManagerSessionEvent

  @Serializable
  data class ProcessStopped(val sessionId: XDebugSessionId) : XDebuggerManagerSessionEvent

  @Serializable
  data class CurrentSessionChanged(val previousSession: XDebugSessionId?, val currentSession: XDebugSessionId?) : XDebuggerManagerSessionEvent
}

@ApiStatus.Internal
@Serializable
sealed interface XDebuggerSessionEvent {
  @Serializable
  sealed interface EventWithState : XDebuggerSessionEvent {
    val state: XDebugSessionState
  }

  @Serializable
  class SessionPaused(
    override val state: XDebugSessionState,
    val suspendData: SuspendData?,
  ) : EventWithState

  @Serializable
  class SessionResumed(
    override val state: XDebugSessionState,
  ) : EventWithState

  @Serializable
  class SessionStopped(
    override val state: XDebugSessionState,
  ) : EventWithState

  @Serializable
  class StackFrameChanged(
    override val state: XDebugSessionState,
    val sourcePositionDto: XSourcePositionDto?,
    val topSourcePositionDto: XSourcePositionDto?,
    val isTopFrame: Boolean,
    val stackFrame: XStackFrameDto?,
  ) : EventWithState

  @Serializable
  class BeforeSessionResume(
    override val state: XDebugSessionState,
  ) : EventWithState

  @Serializable
  object SettingsChanged : XDebuggerSessionEvent

  @Serializable
  data class BreakpointsMuted(val muted: Boolean) : XDebuggerSessionEvent
}

@ApiStatus.Internal
@Serializable
data class SuspendData(
  val suspendContextDto: XSuspendContextDto,
  val executionStack: XExecutionStackDto?,
  val stackFrame: XStackFrameDto?,
  val topSourcePositionDto: XSourcePositionDto?,
)

@ApiStatus.Internal
@Serializable
data class XDebugSessionsList(
  val list: List<XDebugSessionDto>,
  val eventFlow: RpcFlow<XDebuggerManagerSessionEvent>,
)
