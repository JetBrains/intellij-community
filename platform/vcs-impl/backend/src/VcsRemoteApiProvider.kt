// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.backend

import com.intellij.openapi.components.service
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListsApi
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewApi
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewDiffApi
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewInclusionModelApi
import com.intellij.platform.vcs.impl.shared.rpc.PartialChangesApi
import com.intellij.platform.vcs.impl.shared.rpc.VcsMappingsApi
import fleet.rpc.remoteApiDescriptor

/**
 * Serves the VCS remote APIs to a frontend.
 *
 * Each implementation is an application service of `intellij.platform.vcs.impl`.
 * A light IDE without a backend reaches the same service through `LiteRemoteApiProviderService.awaitWithLocalFallback`.
 */
internal class VcsRemoteApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<ChangeListsApi>()) { service<ChangeListsApi>() }
    remoteApi(remoteApiDescriptor<VcsMappingsApi>()) { service<VcsMappingsApi>() }
    remoteApi(remoteApiDescriptor<PartialChangesApi>()) { service<PartialChangesApi>() }
    remoteApi(remoteApiDescriptor<ChangesViewApi>()) { service<ChangesViewApi>() }
    remoteApi(remoteApiDescriptor<ChangesViewDiffApi>()) { service<ChangesViewDiffApi>() }
    remoteApi(remoteApiDescriptor<ChangesViewInclusionModelApi>()) { service<ChangesViewInclusionModelApi>() }
  }
}

