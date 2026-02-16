// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XMixedModeApi : RemoteApi<Unit> {
  suspend fun isMixedModeSession(sessionId: XDebugSessionId): Boolean
  suspend fun showCustomizedEvaluatorView(frameId: XStackFrameId) : Boolean

  companion object {
    @JvmStatic
    suspend fun getInstance(): XMixedModeApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XMixedModeApi>())
    }
  }
}