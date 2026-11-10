package com.intellij.grazie.cloud

import com.intellij.openapi.extensions.ExtensionPointName
import io.ktor.client.HttpClient
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

data class GrazieCloudApi(val baseApiUrl: String, val httpClient: HttpClient)

interface GrazieCloudConnector {
  /**
   * Returns true if there is a connection to Grazie Cloud.
   */
  fun seemsCloudConnected(): Boolean

  /**
   * Returns the connection to the JetBrains AI gateway.
   */
  fun api(): GrazieCloudApi?

  /**
   * Returns true if there is a quota available for the current user.
   */
  fun hasQuota(): Boolean

  companion object {
    private val EP_NAME: ExtensionPointName<GrazieCloudConnector> = ExtensionPointName("com.intellij.grazie.cloudConnector")

    /**
     * Returns true if there is a connection to Grazie Cloud.
     */
    fun seemsCloudConnected(): Boolean = choose(EP_NAME.extensionList)?.seemsCloudConnected() ?: false

    fun hasQuota(): Boolean = choose(EP_NAME.extensionList)?.hasQuota() ?: false

    fun api(): GrazieCloudApi? = choose(EP_NAME.extensionList)?.api()

    /**
     * Chooses the connector that serves a request: the first one that seems connected, or else the first one.
     * Thus several connectors can be registered at the same time.
     */
    @ApiStatus.Internal
    @VisibleForTesting
    fun choose(connectors: List<GrazieCloudConnector>): GrazieCloudConnector? =
      connectors.firstOrNull { it.seemsCloudConnected() } ?: connectors.firstOrNull()
  }
}