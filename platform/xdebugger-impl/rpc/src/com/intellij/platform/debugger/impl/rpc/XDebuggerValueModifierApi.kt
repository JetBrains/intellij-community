// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.xdebugger.impl.rpc.XValueId
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
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XDebuggerValueModifierApi>())
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