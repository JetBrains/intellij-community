package com.intellij.grazie.cloud

import ai.grazie.api.gateway.client.SuspendableAPIGatewayClient
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieConfig.State.Processing
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface GrazieCloudConnector {
  /**
   * Returns true if there is a connection to Grazie Cloud.
   */
  fun isAuthorized(): Boolean

  /**
   * Connects to Grazie Cloud. Returns true if the connection was established successfully and false otherwise.
   */
  fun connect(project: Project): Boolean

  /**
   * Returns the default value for cloud connection.
   */
  fun isCloudEnabledByDefault(): Boolean

  /**
   * Asks user for consent for using Cloud mode.
   */
  fun askUserConsentForCloud(): Boolean

  /**
   * Returns the API Gateway client.
   */
  fun api(): SuspendableAPIGatewayClient?

  /**
   * Subscribe to authorization state change events.
   */
  fun subscribeToAuthorizationStateEvents(disposable: Disposable, listener: () -> Unit)

  companion object {
    private val EP_NAME: ExtensionPointName<GrazieCloudConnector> = ExtensionPointName("com.intellij.grazie.cloudConnector")

    fun hasAdditionalConnectors(): Boolean = EP_NAME.extensionList.size > 1

    fun isAuthorized(): Boolean = EP_NAME.extensionList.first().isAuthorized()

    /**
     * Returns true if there is a connection to Grazie Cloud and processing is [Processing.Cloud].
     */
    fun seemsCloudConnected(): Boolean {
      val connector = EP_NAME.extensionList.first()
      return connector.isAuthorized() && GrazieConfig.get().processing == Processing.Cloud
    }

    fun connect(project: Project): Boolean =
      EP_NAME.extensionList.first().connect(project)

    fun isCloudEnabledByDefault(): Boolean = EP_NAME.extensionList.first().isCloudEnabledByDefault()

    fun askUserConsentForCloud(): Boolean = EP_NAME.extensionList.first().askUserConsentForCloud()

    fun isAfterRecentGecError(): Boolean = GrazieCloudConnectionState.isAfterRecentGecError()

    fun api(): SuspendableAPIGatewayClient? = EP_NAME.extensionList.first().api()

    fun subscribeToAuthorizationStateEvents(disposable: Disposable, listener: () -> Unit): Unit =
      EP_NAME.forEachExtensionSafe { it.subscribeToAuthorizationStateEvents(disposable, listener) }
  }
}