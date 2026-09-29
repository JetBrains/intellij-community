// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.backend

import com.intellij.dvcs.rpc.VcsRepositoryColorsApi
import com.intellij.openapi.components.service
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

/**
 * Serves the DVCS remote API to a frontend.
 *
 * The implementation is an application service of `intellij.platform.vcs.dvcs.impl`.
 * A light IDE without a backend reaches the same service through `LiteRemoteApiProviderService.awaitWithLocalFallback`.
 */
internal class DvcsRemoteApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<VcsRepositoryColorsApi>()) { service<VcsRepositoryColorsApi>() }
  }
}
