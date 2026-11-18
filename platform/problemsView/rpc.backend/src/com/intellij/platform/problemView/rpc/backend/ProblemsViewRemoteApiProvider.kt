// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemView.rpc.backend

import com.intellij.openapi.components.service
import com.intellij.platform.problemsView.shared.ProblemsViewApi
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor

/**
 * Serves [ProblemsViewApi] to a frontend.
 *
 * The implementation is an application service of `intellij.platform.problemView.backend`.
 * A light IDE without a backend reaches the same service through `LiteRemoteApiProviderService.awaitWithOptionalLocalFallback`.
 */
internal class ProblemsViewRemoteApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<ProblemsViewApi>()) { service<ProblemsViewApi>() }
  }
}
