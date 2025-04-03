// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XValueApi : RemoteApi<Unit> {
  suspend fun computeChildren(xValueId: XValueId): Flow<XValueComputeChildrenEvent>

  suspend fun disposeXValue(xValueId: XValueId)

  suspend fun evaluateFullValue(xValueId: XValueId): Deferred<XFullValueEvaluatorResult>

  suspend fun computeExpression(xValueId: XValueId): XExpressionDto?

  companion object {
    @JvmStatic
    suspend fun getInstance(): XValueApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XValueApi>())
    }
  }
}