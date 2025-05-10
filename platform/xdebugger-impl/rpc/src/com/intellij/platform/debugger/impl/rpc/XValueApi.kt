// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.util.ThreeState
import com.intellij.xdebugger.impl.rpc.XExpressionDto
import com.intellij.xdebugger.impl.rpc.XSourcePositionDto
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.rpc.XValueSerializedPresentation
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.RpcFlow
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XValueApi : RemoteApi<Unit> {
  suspend fun computeTooltipPresentation(xValueId: XValueId): Flow<XValueSerializedPresentation>

  suspend fun computeChildren(xValueId: XValueId): Flow<XValueComputeChildrenEvent>

  suspend fun disposeXValue(xValueId: XValueId)

  suspend fun evaluateFullValue(xValueId: XValueId): Deferred<XFullValueEvaluatorResult>

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
data class XInlineDebuggerDataDto(
  val canCompute: ThreeState,
  val sourcePositionsFlow: RpcFlow<XSourcePositionDto>,
)
