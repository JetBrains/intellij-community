// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.UID
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XBreakpointApi : RemoteApi<Unit> {
  suspend fun setEnabled(breakpointId: XBreakpointId, enabled: Boolean)

  companion object {
    @JvmStatic
    suspend fun getInstance(): XBreakpointApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XBreakpointApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
data class XBreakpointId(override val uid: UID) : Id