// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.UID
import com.intellij.util.ThreeState
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.RpcFlow
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XValueApi : RemoteApi<Unit> {
  suspend fun computeTooltipPresentation(xValueId: XValueId): Flow<XValueSerializedPresentation>

  fun computeChildren(id: XContainerId): Flow<XValueComputeChildrenEvent>
  fun computeExpandedChildren(frameId: XStackFrameId, root: XDebuggerTreeExpandedNode): Flow<PreloadChildrenEvent>

  suspend fun disposeXValue(xValueId: XValueId)

  suspend fun evaluateFullValue(xValueId: XValueId): Flow<XFullValueEvaluatorResult>

  suspend fun computeExpression(xValueId: XValueId): XExpressionDto?

  suspend fun computeSourcePosition(xValueId: XValueId): XSourcePositionDto?
  suspend fun computeTypeSourcePosition(xValueId: XValueId): XSourcePositionDto?
  suspend fun computeInlineData(xValueId: XValueId): XInlineDebuggerDataDto?

  companion object {
    @JvmStatic
    suspend fun getInstance(): XValueApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XValueApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
sealed interface XContainerId : Id

/**
 * @see com.intellij.xdebugger.impl.rpc.models.BackendXValueModel
 */
@ApiStatus.Internal
@Serializable
data class XValueId(override val uid: UID) : XContainerId

/** @see com.intellij.xdebugger.impl.rpc.models.BackendXValueGroupModel */
@ApiStatus.Internal
@Serializable
data class XValueGroupId(override val uid: UID) : XContainerId

@ApiStatus.Internal
@Serializable
data class XInlineDebuggerDataDto(
  val canCompute: ThreeState,
  val sourcePositionsFlow: RpcFlow<XSourcePositionDto>,
)

@ApiStatus.Internal
@Serializable
data class XDebuggerTreeExpandedNode(
  val name: String,
  val children: List<XDebuggerTreeExpandedNode>,
)

@ApiStatus.Internal
@Serializable
sealed interface PreloadChildrenEvent {
  @Serializable
  data class ToBePreloaded(val id: XContainerId) : PreloadChildrenEvent

  @Serializable
  data class ExpandedChildrenEvent(val id: XContainerId, val event: XValueComputeChildrenEvent) : PreloadChildrenEvent
}
