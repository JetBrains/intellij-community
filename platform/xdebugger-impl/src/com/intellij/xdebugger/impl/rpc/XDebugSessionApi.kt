// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.execution.rpc.ExecutionEnvironmentProxyDto
import com.intellij.execution.rpc.ProcessHandlerDto
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.ide.rpc.BackendDocumentId
import com.intellij.ide.rpc.FrontendDocumentId
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.UID
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.impl.ui.XDebugSessionTab
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.RpcFlow
import fleet.rpc.core.SendChannelSerializer
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XDebugSessionApi : RemoteApi<Unit> {
  suspend fun currentSourcePosition(sessionId: XDebugSessionId): Flow<XSourcePositionDto?>

  suspend fun topSourcePosition(sessionId: XDebugSessionId): Flow<XSourcePositionDto?>

  suspend fun currentSessionState(sessionId: XDebugSessionId): Flow<XDebugSessionState>

  suspend fun createDocument(frontendDocumentId: FrontendDocumentId, sessionId: XDebugSessionId, expression: XExpressionDto, sourcePosition: XSourcePositionDto?, evaluationMode: EvaluationMode): BackendDocumentId?

  suspend fun sessionTabInfo(sessionId: XDebugSessionId): Flow<XDebuggerSessionTabDto?>

  suspend fun resume(sessionId: XDebugSessionId)

  suspend fun pause(sessionId: XDebugSessionId)

  suspend fun stepOver(sessionId: XDebugSessionId, ignoreBreakpoints: Boolean)

  suspend fun stepOut(sessionId: XDebugSessionId)

  suspend fun forceStepInto(sessionId: XDebugSessionId)

  suspend fun runToPosition(sessionId: XDebugSessionId, sourcePositionDto: XSourcePositionDto, ignoreBreakpoints: Boolean)

  suspend fun triggerUpdate(sessionId: XDebugSessionId)

  suspend fun updateExecutionPosition(sessionId: XDebugSessionId)

  suspend fun onTabInitialized(sessionId: XDebugSessionId, tabInfo: XDebuggerSessionTabInfoCallback)

  suspend fun setCurrentStackFrame(sessionId: XDebugSessionId, executionStackId: XExecutionStackId, frameId: XStackFrameId, isTopFrame: Boolean)

  suspend fun computeExecutionStacks(suspendContextId: XSuspendContextId): Flow<XExecutionStacksEvent>

  suspend fun getFileColorsFlow(sessionId: XDebugSessionId): Flow<XFileColorDto>
  suspend fun scheduleFileColorComputation(sessionId: XDebugSessionId, virtualFileId: VirtualFileId)

  suspend fun showExecutionPoint(sessionId: XDebugSessionId)

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebugSessionApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XDebugSessionApi>())
    }
  }
}

/**
 * @see XDebugSessionId.findValue
 * @see com.intellij.xdebugger.impl.XDebugSessionImpl.id
 */
@ApiStatus.Internal
@Serializable
data class XDebugSessionId(override val uid: UID) : Id

@ApiStatus.Internal
@Serializable
data class XDebugSessionDto(
  val id: XDebugSessionId,
  val editorsProviderDto: XDebuggerEditorsProviderDto,
  val initialSessionState: XDebugSessionState,
  val sessionName: String,
  val sessionEvents: RpcFlow<XDebuggerSessionEvent>,
  val sessionDataDto: XDebugSessionDataDto,
  val consoleViewData: XDebuggerConsoleViewData?,
  val processHandlerDto: ProcessHandlerDto,
)


// TODO: should be moved to platform
@ApiStatus.Internal
@Serializable
data class KillableProcessInfo(
  val canKillProcess: Boolean = true,
)

@ApiStatus.Internal
@Serializable
data class XDebugSessionDataDto(
  val configurationName: String,
)

@ApiStatus.Internal
@Serializable
data class XDebugSessionState(
  val isPaused: Boolean,
  val isStopped: Boolean,
  val isReadOnly: Boolean,
  val isPauseActionSupported: Boolean,
  val isSuspended: Boolean,
)

@ApiStatus.Internal
@Serializable
data class XDebuggerEditorsProviderDto(
  val fileTypeId: String,
  // TODO[IJPL-160146]: support [XDebuggerEditorsProvider] for local case in the same way as for remote
  @Transient val editorsProvider: XDebuggerEditorsProvider? = null,
)

@ApiStatus.Internal
@Serializable
sealed interface XDebuggerSessionTabAbstractInfo

@ApiStatus.Internal
@Serializable
data class XDebuggerSessionTabInfoNoInit(
  @Transient val tab: XDebugSessionTab? = null,
) : XDebuggerSessionTabAbstractInfo

@ApiStatus.Internal
@Serializable
data class XDebuggerSessionTabInfoCallback(
  @Transient val tab: XDebugSessionTab? = null,
)

@ApiStatus.Internal
@Serializable
data class XDebuggerSessionTabInfo(
  val iconId: IconId?,
  val forceNewDebuggerUi: Boolean,
  val withFramesCustomization: Boolean,
  // TODO pass to frontend
  @Transient val contentToReuse: RunContentDescriptor? = null,
  val executionEnvironmentProxyDto: ExecutionEnvironmentProxyDto?,
  @Serializable(with = SendChannelSerializer::class) val tabClosedCallback: SendChannel<Unit>,
) : XDebuggerSessionTabAbstractInfo

@ApiStatus.Internal
@Serializable
data class XDebuggerSessionTabDto(
  val tabInfo: XDebuggerSessionTabAbstractInfo,
  val pausedInfo: RpcFlow<XDebugSessionPausedInfo?>,
)

@ApiStatus.Internal
@Serializable
data class XDebugSessionPausedInfo(
  val pausedByUser: Boolean,
  val topFrameIsAbsent: Boolean,
)

/**
 * @see com.intellij.xdebugger.impl.rpc.models.XSuspendContextModel
 */
@ApiStatus.Internal
@Serializable
data class XSuspendContextId(override val uid: UID) : Id

@ApiStatus.Internal
@Serializable
data class XSuspendContextDto(
  val id: XSuspendContextId,
  val isStepping: Boolean,
)
