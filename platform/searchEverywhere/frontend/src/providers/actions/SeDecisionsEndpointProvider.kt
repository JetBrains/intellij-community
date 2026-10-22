// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * A plugin that serves the OpenAI Decision API through a proxy of its own publishes the endpoint of that proxy.
 *
 * The proxy owns the credential and the gateway route. Search Everywhere holds no token for it,
 * and it sends each Decision API request to the endpoint with the headers of the endpoint.
 *
 * **This is a contract between plugins with different release cycles.** The Air plugin implements it, and one Air build
 * runs against the Search Everywhere of a wide range of IDE versions. Keep this point and [SeDecisionsEndpointLease] binary compatible:
 *
 * - Never remove, rename, or change a member, a parameter, or a return type.
 * - Add a member only with a default implementation.
 * - Keep the extension point name.
 *
 * Search Everywhere asks the providers in the extension order, and the first lease serves the popup.
 * See [acquireDecisionsEndpoint].
 */
@Internal
interface SeDecisionsEndpointProvider {
  val id: String
  suspend fun acquire(): SeDecisionsEndpointLease?

  companion object {
    val EP_NAME: ExtensionPointName<SeDecisionsEndpointProvider> =
      ExtensionPointName("com.intellij.searchEverywhere.decisionsEndpointProvider")
  }
}

/**
 * One acquired Decision API endpoint. See [SeDecisionsEndpointProvider] for the compatibility rules.
 *
 * An implementation must not print a header value in `toString`, because a header can carry a secret.
 */
@Internal
interface SeDecisionsEndpointLease {
  val url: String
  val headers: Map<String, String>
  suspend fun close()
}
