// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.ide.rpc.awaitWithOptionalLocalFallback
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XDebuggerValueModifierApi : RemoteApi<Unit> {
  suspend fun setValue(xValueId: XValueId, xExpressionDto: XExpressionDto): TimeoutSafeResult<SetValueResult>

  suspend fun initialValueEditorText(xValueId: XValueId): String?

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebuggerValueModifierApi {
      return LiteRemoteApiProviderService.awaitWithOptionalLocalFallback(remoteApiDescriptor<XDebuggerValueModifierApi>()) { serviceOrNull<XDebuggerValueModifierApi>() }
    }
  }
}

@ApiStatus.Internal
@Serializable
sealed interface SetValueResult {
  @Serializable
  object Success : SetValueResult

  @Serializable
  data class ErrorOccurred(val message: @NlsContexts.DialogMessage String) : SetValueResult
}