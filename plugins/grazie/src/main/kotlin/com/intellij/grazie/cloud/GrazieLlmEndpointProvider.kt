package com.intellij.grazie.cloud

import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * A plugin that serves Grazie through an LLM proxy of its own publishes the endpoint of that proxy.
 *
 * The proxy owns the credential and the gateway route. Grazie holds no token for it, and it sends each
 * request to the endpoint with the headers of the endpoint. [GrazieLlmEndpointCloudConnector] turns the
 * endpoint into a [GrazieCloudConnector].
 *
 * **This is a contract between plugins with different release cycles.** The Air plugin implements it, and
 * one Air build runs against the Grazie plugin of a wide range of IDE versions. Keep this point and
 * [GrazieLlmEndpoint] binary compatible:
 *
 * - Never remove, rename, or change a member, a parameter, or a return type.
 * - Add a member only with a default implementation.
 * - Keep the extension point name.
 */
@ApiStatus.Internal
interface GrazieLlmEndpointProvider {
  /**
   * The endpoint that serves Grazie now, or `null` when this provider serves nothing now. The flow emits the
   * current endpoint to a new collector, and then each change.
   *
   * [GrazieLlmEndpointState] collects the flow and keeps the current value, so a provider needs no state of its own.
   * The first collection is the demand of Grazie for the endpoint.
   */
  val endpoint: Flow<GrazieLlmEndpoint?>

  companion object {
    @VisibleForTesting
    val EP_NAME: ExtensionPointName<GrazieLlmEndpointProvider> = ExtensionPointName("com.intellij.grazie.llmEndpointProvider")
  }
}

/**
 * One Grazie endpoint of a proxy. See [GrazieLlmEndpointProvider] for the compatibility rules.
 *
 * [toString] prints no header value, because a header can carry a secret.
 */
@ApiStatus.Internal
class GrazieLlmEndpoint(
  /**
   * The base URL of the proxy route. Grazie appends the path of the gateway operation, so the provider
   * knows no operation path.
   */
  val url: String,
  /** The headers that every request to [url] carries. */
  val headers: Map<String, String>,
) {
  override fun equals(other: Any?): Boolean = other is GrazieLlmEndpoint && url == other.url && headers == other.headers

  override fun hashCode(): Int = 31 * url.hashCode() + headers.hashCode()

  override fun toString(): String = "GrazieLlmEndpoint(url=$url, headers=${headers.keys})"
}
