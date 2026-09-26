// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.openapi.components.service
import com.intellij.platform.recentFiles.shared.FileSwitcherApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

/**
 * Serves the application service that the shared module registers behind [FileSwitcherApi] over RPC,
 * so a connected frontend and a local caller see the same model.
 */
internal class RecentFilesBackendApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<FileSwitcherApi>()) {
      service<FileSwitcherApi>()
    }
  }
}
