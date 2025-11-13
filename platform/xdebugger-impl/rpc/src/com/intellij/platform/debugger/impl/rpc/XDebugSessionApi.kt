// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.rpc.ProcessHandlerDto
import com.intellij.ide.rpc.AnActionId
import com.intellij.ide.rpc.FrontendDocumentId
import com.intellij.ide.rpc.util.TextRangeId
import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.UID
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XDescriptor
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.DeferredSerializer
import fleet.rpc.core.RpcFlow
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
@Rpc
interface XDebugSessionApi : RemoteApi<Unit> {
  suspend fun createDocument(frontendDocumentId: FrontendDocumentId, sessionId: XDebugSessionId, expression: XExpressionDto, sourcePosition: XSourcePositionDto?, evaluationMode: EvaluationMode): XExpressionDocumentDto?
  suspend fun supportedLanguages(projectId: ProjectId, editorsProviderId: XDebuggerEditorsProviderId, sourcePositionDto: XSourcePositionDto?): List<LanguageDto>

  suspend fun resume(sessionId: XDebugSessionId)

  suspend fun pause(sessionId: XDebugSessionId)

  suspend fun stepOver(sessionId: XDebugSessionId, ignoreBreakpoints: Boolean)

  suspend fun stepOut(sessionId: XDebugSessionId)

  suspend fun stepInto(sessionId: XDebugSessionId)

  suspend fun smartStepInto(smartStepTargetId: XSmartStepIntoTargetId)
  suspend fun smartStepIntoEmpty(sessionId: XDebugSessionId)
  suspend fun computeSmartStepTargets(sessionId: XDebugSessionId): List<XSmartStepIntoTargetDto>
  suspend fun computeStepTargets(sessionId: XDebugSessionId): List<XSmartStepIntoTargetDto>

  suspend fun forceStepInto(sessionId: XDebugSessionId)

  suspend fun runToPosition(sessionId: XDebugSessionId, sourcePositionDto: XSourcePositionDto, ignoreBreakpoints: Boolean)

  suspend fun triggerUpdate(sessionId: XDebugSessionId)

  suspend fun setCurrentStackFrame(sessionId: XDebugSessionId, executionStackId: XExecutionStackId, frameId: XStackFrameId, isTopFrame: Boolean, changedByUser: Boolean = false)

  suspend fun computeExecutionStacks(suspendContextId: XSuspendContextId): Flow<XExecutionStacksEvent>

  suspend fun muteBreakpoints(sessionDataId: XDebugSessionDataId, muted: Boolean)

  suspend fun getUiUpdateEventsFlow(sessionId: XDebugSessionId): Flow<Unit>

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebugSessionApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XDebugSessionApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
data class XDebugSessionDto(
  val id: XDebugSessionId,
  val runContentDescriptorId: RunContentDescriptorIdImpl?,
  val editorsProviderDto: XDebuggerEditorsProviderDto,
  val initialSessionState: XDebugSessionState,
  val initialSuspendData: SuspendData?,
  val sessionName: String,
  val sessionEvents: RpcFlow<XDebuggerSessionEvent>,
  val sessionDataDto: XDebugSessionDataDto,
  val consoleViewData: XDebuggerConsoleViewData?,
  val processHandlerDto: ProcessHandlerDto,
  val smartStepIntoHandlerDto: XSmartStepIntoHandlerDto?,
  val isLibraryFrameFilterSupported: Boolean,
  val isValuesCustomSorted: Boolean,
  val activeNonLineBreakpointIdFlow: RpcFlow<XBreakpointId?>,
  val restartActions: List<AnActionId>,
  val extraActions: List<AnActionId>,
  val extraStopActions: List<AnActionId>,
)

@ApiStatus.Internal
@Serializable
sealed interface XExecutionStacksEvent {
  @Serializable
  data class NewExecutionStacks(val stacks: List<XExecutionStackDto>, val last: Boolean) : XExecutionStacksEvent

  @Serializable
  data class ErrorOccurred(val errorMessage: @NlsContexts.DialogMessage String) : XExecutionStacksEvent
}

@ApiStatus.Internal
@Serializable
data class XExecutionStackDto(
  val executionStackId: XExecutionStackId,
  val displayName: @Nls String,
  val icon: IconId?,
  @Serializable(with = DeferredSerializer::class) val descriptor: Deferred<XDescriptor>?,
  @Serializable(with = DeferredSerializer::class) val topFrame: Deferred<XStackFrameDto?>,
)

@ApiStatus.Internal
@Serializable
data class XDebugSessionDataId(override val uid: UID) : Id

@ApiStatus.Internal
@Serializable
data class XDebugSessionDataDto(
  val id: XDebugSessionDataId,
  val configurationName: String,
  val initialBreakpointsMuted: Boolean,
  val breakpointsMutedFlow: RpcFlow<Boolean>,
)

@ApiStatus.Internal
@Serializable
data class XDebugSessionState(
  val isPaused: Boolean,
  val isStopped: Boolean,
  val isReadOnly: Boolean,
  val isPauseActionSupported: Boolean,
  val isSuspended: Boolean,
  val isStepOverActionAllowed: Boolean,
  val isStepOutActionAllowed: Boolean,
  val isRunToCursorActionAllowed: Boolean,
)

@ApiStatus.Internal
@Serializable
data class XDebuggerEditorsProviderId(override val uid: UID) : Id

@ApiStatus.Internal
@Serializable
data class XDebuggerEditorsProviderDto(
  val id: XDebuggerEditorsProviderId,
  val fileTypeId: String,
  // TODO[IJPL-160146]: support [XDebuggerEditorsProvider] for local case in the same way as for remote
  @Transient val editorsProvider: XDebuggerEditorsProvider? = null,
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

@ApiStatus.Internal
@Serializable
data class XSmartStepIntoHandlerDto(
  @NlsContexts.PopupTitle val title: String,
)

@ApiStatus.Internal
@Serializable
data class XSmartStepIntoTargetDto(
  val id: XSmartStepIntoTargetId,
  val iconId: IconId?,
  val text: @NlsSafe String,
  val description: @Nls String?,
  val textRange: TextRangeId?,
  val needsForcedSmartStepInto: Boolean,
)

@ApiStatus.Internal
@Serializable
data class XSmartStepIntoTargetId(override val uid: UID) : Id
