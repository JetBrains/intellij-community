// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.xdebugger.impl.rpc.XValueId
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XDebuggerNavigationApi : RemoteApi<Unit> {
  suspend fun navigateToXValue(xValueId: XValueId): TimeoutSafeResult<Boolean>

  suspend fun navigateToXValueType(xValueId: XValueId): TimeoutSafeResult<Boolean>

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebuggerNavigationApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XDebuggerNavigationApi>())
    }
  }
}
