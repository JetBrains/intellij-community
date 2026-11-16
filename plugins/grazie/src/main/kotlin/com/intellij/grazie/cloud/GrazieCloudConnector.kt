package com.intellij.grazie.cloud

import com.intellij.openapi.extensions.ExtensionPointName
import io.ktor.client.HttpClient

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
    fun seemsCloudConnected(): Boolean {
      val connector = EP_NAME.extensionList.firstOrNull() ?: return false
      return connector.seemsCloudConnected()
    }

    fun hasQuota(): Boolean = EP_NAME.extensionList.firstOrNull()?.hasQuota() ?: false

    fun api(): GrazieCloudApi? = EP_NAME.extensionList.firstOrNull()?.api()
  }
}