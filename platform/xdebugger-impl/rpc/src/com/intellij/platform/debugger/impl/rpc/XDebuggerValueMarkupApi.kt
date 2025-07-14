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
interface XDebuggerValueMarkupApi : RemoteApi<Unit> {
  suspend fun markValue(xValueId: XValueId, markerDto: XValueMarkerDto)

  suspend fun unmarkValue(xValueId: XValueId)

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebuggerValueMarkupApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XDebuggerValueMarkupApi>())
    }
  }
}