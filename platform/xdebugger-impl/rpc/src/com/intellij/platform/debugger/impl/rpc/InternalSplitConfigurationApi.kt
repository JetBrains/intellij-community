// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface InternalSplitConfigurationApi : RemoteApi<Unit> {
  suspend fun isSplitDebuggersEnabled(): Boolean
  suspend fun setSplitDebuggersEnabled(enabled: Boolean)

  companion object {
    @JvmStatic
    suspend fun getInstance(): InternalSplitConfigurationApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<InternalSplitConfigurationApi>())
    }
  }
}
