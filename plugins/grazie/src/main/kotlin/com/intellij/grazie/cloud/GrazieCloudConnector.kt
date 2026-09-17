package com.intellij.grazie.cloud

import ai.grazie.api.gateway.client.SuspendableAPIGatewayClient
import com.intellij.openapi.extensions.ExtensionPointName

interface GrazieCloudConnector {
  /**
   * Returns true if there is a connection to Grazie Cloud.
   */
  fun seemsCloudConnected(): Boolean

  /**
   * Returns the API Gateway client.
   */
  fun api(): SuspendableAPIGatewayClient?

  /**
   * Returns true if there is a quota available for the current user.
   */
  fun hasQuota(): Boolean

  companion object {
    private val EP_NAME: ExtensionPointName<GrazieCloudConnector> = ExtensionPointName("com.intellij.grazie.cloudConnector")

    /**
     * Returns true if there is a connection to Grazie Cloud.
     */
    fun seemsCloudConnected(): Boolean {
      val connector = EP_NAME.extensionList.firstOrNull() ?: return false
      return connector.seemsCloudConnected()
    }

    fun hasQuota(): Boolean = EP_NAME.extensionList.firstOrNull()?.hasQuota() ?: false

    fun api(): SuspendableAPIGatewayClient? = EP_NAME.extensionList.firstOrNull()?.api()
  }
}