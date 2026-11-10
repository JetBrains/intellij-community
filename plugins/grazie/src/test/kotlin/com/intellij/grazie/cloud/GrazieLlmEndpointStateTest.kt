package com.intellij.grazie.cloud

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import kotlin.time.Duration.Companion.seconds

/** Covers the endpoint state of Grazie and the restart of the highlighting when the endpoint changes. */
@Timeout(30)
class GrazieLlmEndpointStateTest {
  private val provider = FakeEndpointProvider()
  private val providers = MutableStateFlow<List<GrazieLlmEndpointProvider>>(listOf(provider))
  private val restarts = Channel<Unit>(Channel.UNLIMITED)

  @Test
  fun `no publication keeps the state empty and restarts nothing`(): Unit = withState { state ->
    repeat(10) { yield() }

    assertThat(state.endpoint.value).isNull()
    assertThat(restarts.tryReceive().isSuccess).isFalse()
  }

  @Test
  fun `a publication sets the endpoint and restarts the highlighting`(): Unit = withState { state ->
    assertThat(state.endpoint.value).isNull()

    provider.endpoint.value = ENDPOINT

    awaitRestart()
    assertThat(state.endpoint.value).isEqualTo(ENDPOINT)
  }

  @Test
  fun `a revoked publication clears the endpoint and restarts the highlighting`(): Unit = withState { state ->
    provider.endpoint.value = ENDPOINT
    awaitRestart()

    provider.endpoint.value = null

    awaitRestart()
    assertThat(state.endpoint.value).isNull()
  }

  @Test
  fun `a provider that loads later sets the endpoint`(): Unit = withState { state ->
    providers.value = listOf(provider, FakeEndpointProvider().also { it.endpoint.value = ENDPOINT })

    awaitRestart()
    assertThat(state.endpoint.value).isEqualTo(ENDPOINT)
  }

  @Test
  fun `a provider that unloads clears the endpoint`(): Unit = withState { state ->
    provider.endpoint.value = ENDPOINT
    awaitRestart()

    providers.value = emptyList()

    awaitRestart()
    assertThat(state.endpoint.value).isNull()
  }

  @Test
  fun `a provider that emits nothing does not hold back the other providers`(): Unit = withState { state ->
    providers.value = listOf(SilentEndpointProvider(), provider)
    provider.endpoint.value = ENDPOINT

    awaitRestart()
    assertThat(state.endpoint.value).isEqualTo(ENDPOINT)
  }

  private fun withState(body: suspend (GrazieLlmEndpointState) -> Unit): Unit = runBlocking {
    val job = Job(coroutineContext.job)
    try {
      body(GrazieLlmEndpointState(CoroutineScope(coroutineContext + job), providers) { restarts.trySend(Unit) })
    }
    finally {
      job.cancel()
    }
  }

  private suspend fun awaitRestart() {
    withTimeout(10.seconds) { restarts.receive() }
  }

  private class FakeEndpointProvider : GrazieLlmEndpointProvider {
    override val endpoint: MutableStateFlow<GrazieLlmEndpoint?> = MutableStateFlow(null)
  }

  private class SilentEndpointProvider : GrazieLlmEndpointProvider {
    override val endpoint: Flow<GrazieLlmEndpoint?> = emptyFlow()
  }

  private companion object {
    private val ENDPOINT = GrazieLlmEndpoint("http://127.0.0.1:1234/jcp.ide.grazie.ai_gateway", emptyMap())
  }
}
