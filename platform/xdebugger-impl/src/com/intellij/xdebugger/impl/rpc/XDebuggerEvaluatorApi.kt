// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.platform.kernel.withKernel
import com.intellij.platform.rpc.RemoteApiProviderService
import com.jetbrains.rhizomedb.EID
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XDebuggerEvaluatorApi : RemoteApi<Unit> {
  suspend fun evaluate(evaluatorId: XDebuggerEvaluatorId, expression: String): Deferred<XValueId>?

  suspend fun computePresentation(xValueId: XValueId): Flow<XValuePresentation>?

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebuggerEvaluatorApi {
      return withKernel {
        RemoteApiProviderService.resolve(remoteApiDescriptor<XDebuggerEvaluatorApi>())
      }
    }
  }
}

@ApiStatus.Internal
@Serializable
data class XValueId(val eid: EID)


@ApiStatus.Internal
@Serializable
data class XDebuggerEvaluatorId(val eid: EID)

@ApiStatus.Internal
@Serializable
data class XValuePresentation(val value: String)