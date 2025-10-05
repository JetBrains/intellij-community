// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project

import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface OpenFileChooserApi : RemoteApi<Unit> {
  suspend fun chooseDirectory(projectId: ProjectId, initialDirectory: String): Deferred<String?>

  companion object {
    @JvmStatic
    suspend fun getInstance(): OpenFileChooserApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<OpenFileChooserApi>())
    }
  }
}