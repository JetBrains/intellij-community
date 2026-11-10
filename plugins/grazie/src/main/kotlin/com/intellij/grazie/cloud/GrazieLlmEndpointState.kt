package com.intellij.grazie.cloud

import com.intellij.grazie.spellcheck.async.AsyncUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * Owns the Grazie endpoint: the endpoint of the first [GrazieLlmEndpointProvider] that publishes one.
 *
 * The state starts empty and follows the providers from the first use of this service. It follows the extension
 * list, so it also sees a provider that loads later. Each change of [endpoint] restarts the highlighting, so an
 * inspection that read the old value runs again.
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
class GrazieLlmEndpointState @VisibleForTesting constructor(
  cs: CoroutineScope,
  /** Emits the current providers, and then the providers after each change of the extension list. */
  providers: Flow<List<GrazieLlmEndpointProvider>>,
  restart: () -> Unit,
) {
  /** The container instantiates this one. */
  @Suppress("unused")
  constructor(cs: CoroutineScope) : this(
    cs = cs,
    providers = callbackFlow {
      GrazieLlmEndpointProvider.EP_NAME.addChangeListener(this) { trySend(GrazieLlmEndpointProvider.EP_NAME.extensionList) }
      send(GrazieLlmEndpointProvider.EP_NAME.extensionList)
      awaitClose()
    },
    restart = { AsyncUtils.restartInspection(ApplicationManager.getApplication(), "GrazieLlmEndpoint") },
  )

  private val mutableEndpoint = MutableStateFlow<GrazieLlmEndpoint?>(null)

  /** The endpoint that serves Grazie now, or `null` while no provider publishes one. A read never waits. */
  val endpoint: StateFlow<GrazieLlmEndpoint?> = mutableEndpoint.asStateFlow()

  init {
    @OptIn(ExperimentalCoroutinesApi::class)
    cs.launch {
      providers
        .flatMapLatest(::firstPublishedEndpoint)
        .collect { endpoint -> if (mutableEndpoint.getAndUpdate { endpoint } != endpoint) restart() }
    }
  }
}

/** A provider that has emitted nothing yet counts as empty, so it does not hold back the other providers. */
private fun firstPublishedEndpoint(providers: List<GrazieLlmEndpointProvider>): Flow<GrazieLlmEndpoint?> {
  if (providers.isEmpty()) return flowOf(null)
  return combine(providers.map { it.endpoint.onStart { emit(null) } }) { endpoints -> endpoints.firstNotNullOfOrNull { it } }
}
