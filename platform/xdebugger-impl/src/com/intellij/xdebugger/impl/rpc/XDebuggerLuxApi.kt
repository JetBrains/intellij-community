// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor

@Rpc
interface XDebuggerLuxApi : RemoteApi<Unit> {

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebuggerLuxApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XDebuggerLuxApi>())
    }
  }
}