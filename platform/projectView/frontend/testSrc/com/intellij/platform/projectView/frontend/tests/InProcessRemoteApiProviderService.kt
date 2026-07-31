// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.tests

import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import java.util.concurrent.ConcurrentHashMap

/**
 * A test-owned, in-process [RemoteApiProviderService] that resolves `@Rpc` descriptors to the **real**
 * backend implementations registered on [RemoteApiProvider.EP_NAME] — directly, WITHOUT any transport
 * or byte serialization.
 *
 * This is how the Project View test framework "replaces the RPC" (see [withProjectViewPane]): the real
 * [com.intellij.platform.projectView.pane.FrontendProjectViewPaneAggregator] stays untouched and still
 * goes through the backend RPC implementation (which runs `event.toDTO()`) and then converts back
 * (`dto.toEvent()`). So the DTO conversions run and `@Transient`-property loss happens exactly as in
 * production, but everything is in-process and deterministic.
 *
 * It mirrors the production `RemoteApiRegistry`, but is installed by the test (see
 * [AbstractProjectViewPaneTest]) so it is independent of which `RemoteApiProviderService` the
 * application registers by default (e.g. the frontend-split one that needs a real connection).
 *
 * Implementations are created lazily on [resolve], not eagerly at construction: some registered API
 * factories require a coroutine/progress context that a bare test-setup thread doesn't have, and we
 * only ever need the Project View one anyway.
 */
internal class InProcessRemoteApiProviderService : RemoteApiProviderService {
  private val factories = HashMap<String, () -> RemoteApi<*>>()
  private val instances = ConcurrentHashMap<String, RemoteApi<*>>()

  init {
    val sink = object : RemoteApiProvider.Sink {
      override fun <T : RemoteApi<Unit>> remoteApi(descriptor: RemoteApiDescriptor<T>, implementation: () -> T) {
        factories[descriptor.getApiFqn()] = implementation
      }
    }
    for (provider in RemoteApiProvider.EP_NAME.extensionList) {
      with(provider) { sink.remoteApis() }
    }
  }

  @Suppress("UNCHECKED_CAST")
  override suspend fun <T : RemoteApi<Unit>> resolve(descriptor: RemoteApiDescriptor<T>): T {
    val fqn = descriptor.getApiFqn()
    val factory = factories[fqn]
                  ?: throw IllegalStateException("No remote API registered for '$fqn'. Registered: ${factories.keys}")
    return instances.computeIfAbsent(fqn) { factory() } as T
  }

  override fun listRegisteredApis(): List<String> = factories.keys.toList()

  override fun isServiceOperational(): Boolean = true
}
