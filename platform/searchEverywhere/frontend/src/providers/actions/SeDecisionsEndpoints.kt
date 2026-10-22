// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<SeDecisionsEndpointProvider>()

/**
 * Acquires the Decision API endpoint for one Search Everywhere popup, or returns `null` when nothing serves one now.
 *
 * Search Everywhere holds no credential of its own. The [SeDecisionsEndpointProvider] extensions answer. See [selectDecisionsEndpoint].
 */
internal suspend fun acquireDecisionsEndpoint(): SeDecisionsEndpointLease? {
  return selectDecisionsEndpoint(SeDecisionsEndpointProvider.EP_NAME.extensionList)
}

/**
 * Returns the first lease of [providers], in their order.
 *
 * It never asks the next provider after a lease, because each lease holds resources until it closes.
 * It skips a provider that throws, and it reports one warning for each failing provider id.
 *
 * A cancellation of the caller stops the selection. A provider can also throw [CancellationException] from a scope of its own.
 * While the caller is active, this is a failure of the provider, because `SeLocalItemDataProvider` retries a search that throws it.
 */
internal suspend fun selectDecisionsEndpoint(providers: List<SeDecisionsEndpointProvider>): SeDecisionsEndpointLease? {
  for (provider in providers) {
    @Suppress("IncorrectCancellationExceptionHandling") // ensureActive() rethrows the cancellation of the caller
    val lease = try {
      provider.acquire()
    }
    catch (e: CancellationException) {
      currentCoroutineContext().ensureActive()
      // The logger refuses a cancellation, thus the report wraps it.
      reportProviderFailure(provider, IllegalStateException("The provider was cancelled while the caller is active", e))
      continue
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      reportProviderFailure(provider, e)
      continue
    }
    if (lease != null) {
      LOG.debug { "The Decision API endpoint of '${provider.id}' serves the popup: $lease" }
      return lease
    }
  }
  return null
}

/**
 * Reports one failure for each provider id, because Search Everywhere asks the providers on each query.
 *
 * The record keeps the ids and not the providers, thus it keeps no class of an unloaded plugin.
 */
private fun reportProviderFailure(provider: SeDecisionsEndpointProvider, e: Throwable) {
  if (reportedProviderIds.add(provider.id)) {
    LOG.warn("Search Everywhere could not acquire the Decision API endpoint of '${provider.id}' (${provider.javaClass.name}).", e)
  }
}

private val reportedProviderIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
