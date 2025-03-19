// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus

// TODO: it should be platform API, not debugger's one
@ApiStatus.Internal
@Rpc
interface XDebugSessionProcessHandlerApi : RemoteApi<Unit> {
  suspend fun startNotify(sessionId: XDebugSessionId)

  suspend fun waitFor(sessionId: XDebugSessionId, timeoutInMilliseconds: Long?): Deferred<Boolean>

  suspend fun destroyProcess(sessionId: XDebugSessionId): Deferred<Int?>

  suspend fun detachProcess(sessionId: XDebugSessionId): Deferred<Int?>
  
  suspend fun killProcess(sessionId: XDebugSessionId)

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebugSessionProcessHandlerApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XDebugSessionProcessHandlerApi>())
    }
  }
}