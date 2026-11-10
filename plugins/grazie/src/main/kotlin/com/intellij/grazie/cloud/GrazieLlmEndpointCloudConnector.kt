package com.intellij.grazie.cloud

import com.intellij.openapi.components.service
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * Connects Grazie to the cloud through the endpoint of [GrazieLlmEndpointState].
 *
 * Grazie holds no token here. The proxy behind the endpoint adds the credential, and the client of this
 * connector only sends the endpoint headers with each request.
 *
 * The endpoint URL is the root of the JetBrains AI gateway for the selected access. The proxy adds the
 * path prefix that the access needs.
 */
@ApiStatus.Internal
class GrazieLlmEndpointCloudConnector @VisibleForTesting constructor(
  private val endpoint: () -> GrazieLlmEndpoint?,
) : GrazieCloudConnector {
  /** The extension point instantiates this one. */
  @Suppress("unused")
  constructor() : this({ service<GrazieLlmEndpointState>().endpoint.value })

  /**
   * The one client of this connector. It reads the headers of the current endpoint for each request.
   *
   * The analysis sends each request in a `runBlocking` of its own, so a cancel does not stop it. Only the
   * timeouts stop a request.
   */
  private val client: HttpClient by lazy {
    HttpClient(CIO) {
      install(HttpTimeout) {
        connectTimeoutMillis = 10_000
        socketTimeoutMillis = 60_000
      }
      defaultRequest {
        endpoint()?.headers?.forEach { (name, value) -> headers.append(name, value) }
      }
    }
  }

  override fun seemsCloudConnected(): Boolean = endpoint() != null

  override fun api(): GrazieCloudApi? = endpoint()?.let { GrazieCloudApi(it.url.trimEnd('/') + "/", client) }

  /**
   * The proxy owns the quota, so a connected endpoint counts as quota. A request over the quota gets status 477.
   * The plugin of the proxy shows the quota state to the user.
   */
  override fun hasQuota(): Boolean = seemsCloudConnected()
}
