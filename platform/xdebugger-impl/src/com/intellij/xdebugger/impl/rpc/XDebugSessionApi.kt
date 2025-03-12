// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.ide.rpc.BackendDocumentId
import com.intellij.ide.rpc.FrontendDocumentId
import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.impl.ui.XDebugSessionTab
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.RpcFlow
import fleet.rpc.remoteApiDescriptor
import fleet.util.UID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XDebugSessionApi : RemoteApi<Unit> {
  suspend fun currentEvaluator(sessionId: XDebugSessionId): Flow<XDebuggerEvaluatorDto?>

  suspend fun currentSourcePosition(sessionId: XDebugSessionId): Flow<XSourcePositionDto?>

  suspend fun currentSessionState(sessionId: XDebugSessionId): Flow<XDebugSessionState>

  suspend fun createDocument(frontendDocumentId: FrontendDocumentId, sessionId: XDebugSessionId, expression: XExpressionDto, sourcePosition: XSourcePositionDto?, evaluationMode: EvaluationMode): BackendDocumentId?

  suspend fun sessionTabInfo(sessionId: XDebugSessionId): Flow<XDebuggerSessionTabDto?>

  suspend fun resume(sessionId: XDebugSessionId)

  suspend fun pause(sessionId: XDebugSessionId)

  suspend fun stepOver(sessionId: XDebugSessionId, ignoreBreakpoints: Boolean)

  suspend fun triggerUpdate(sessionId: XDebugSessionId)

  suspend fun updateExecutionPosition(sessionId: XDebugSessionId)

  suspend fun onTabInitialized(sessionId: XDebugSessionId, tabInfo: XDebuggerSessionTabInfoCallback)

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebugSessionApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XDebugSessionApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
data class XDebugSessionId(val id: UID)

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
  val processHandlerDto: XDebuggerProcessHandlerDto,
)


// TODO: should be moved to platform
@ApiStatus.Internal
@Serializable
data class KillableProcessInfo(
  val canKillProcess: Boolean = true
)

// TODO: should be moved to platform
@ApiStatus.Internal
@Serializable
data class XDebuggerProcessHandlerDto(
  val detachIsDefault: Boolean,
  val processHandlerEvents: RpcFlow<XDebuggerProcessHandlerEvent>,
  val killableProcessInfo: KillableProcessInfo? = null
)

/**
 * @see com.intellij.execution.process.ProcessListener
 */
@ApiStatus.Internal
@Serializable
sealed interface XDebuggerProcessHandlerEvent {
  @Serializable
  data class StartNotified(val eventData: XDebuggerProcessHandlerEventData) : XDebuggerProcessHandlerEvent

  @Serializable
  data class ProcessTerminated(val eventData: XDebuggerProcessHandlerEventData) : XDebuggerProcessHandlerEvent

  @Serializable
  data class ProcessWillTerminate(val eventData: XDebuggerProcessHandlerEventData, val willBeDestroyed: Boolean) : XDebuggerProcessHandlerEvent

  @Serializable
  data class OnTextAvailable(val eventData: XDebuggerProcessHandlerEventData, val key: String) : XDebuggerProcessHandlerEvent

  @Serializable
  data object ProcessNotStarted : XDebuggerProcessHandlerEvent
}

@ApiStatus.Internal
@Serializable
data class XDebuggerProcessHandlerEventData(
  val text: @NlsSafe String?,
  val exitCode: Int,
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
  @Transient val executionEnvironment: ExecutionEnvironment? = null,
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
