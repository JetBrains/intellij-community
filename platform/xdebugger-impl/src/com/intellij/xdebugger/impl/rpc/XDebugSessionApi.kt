// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.ide.rpc.BackendDocumentId
import com.intellij.ide.rpc.FrontendDocumentId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.impl.ui.XDebugSessionTab
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import fleet.util.UID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

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
  val initialSessionState: XDebugSessionState
)

@ApiStatus.Internal
@Serializable
data class XDebugSessionState(
  val isPaused: Boolean,
  val isStopped: Boolean,
  val isReadOnly: Boolean,
  val isPauseActionSupported: Boolean,
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
data class XDebuggerSessionTabInfo(
  val icon: Icon?,
  val forceNewDebuggerUi: Boolean,
  val withFramesCustomization: Boolean,
  val shouldShowTab: Boolean,
  // TODO pass to frontend
  @Transient val contentToReuse: RunContentDescriptor? = null,
  @Transient val executionEnvironment: ExecutionEnvironment? = null,
) : XDebuggerSessionTabAbstractInfo

@ApiStatus.Internal
@Serializable
data class XDebuggerSessionTabDto(
  val tabInfo: XDebuggerSessionTabAbstractInfo,
)
