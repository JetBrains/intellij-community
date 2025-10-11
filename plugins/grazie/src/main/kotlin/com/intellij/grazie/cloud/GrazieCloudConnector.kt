package com.intellij.grazie.cloud

import ai.grazie.api.gateway.client.SuspendableAPIGatewayClient
import com.intellij.grazie.GrazieConfig.State.Processing
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName

interface GrazieCloudConnector {
  /**
   * Returns true if there is a connection to Grazie Cloud and [connectionType] is [Processing.Cloud].
   */
  fun seemsCloudConnected(): Boolean

  /**
   * Returns the type of the connection. Usually set in settings: ([Processing.Local] or [Processing.Cloud]).
   */
  fun connectionType(): Processing

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
    val EP_NAME = ExtensionPointName<GrazieCloudConnector>("com.intellij.grazie.cloudConnector")

    fun hasCloudConnector(): Boolean = EP_NAME.extensionList.isNotEmpty()

    fun seemsCloudConnected(): Boolean = EP_NAME.extensionList.any { it.seemsCloudConnected() }

    fun isAfterRecentGecError(): Boolean = GrazieCloudConnectionState.isAfterRecentGecError()

    fun api(): SuspendableAPIGatewayClient? = EP_NAME.extensionList.first().api()

    fun subscribeToAuthorizationStateEvents(disposable: Disposable, listener: () -> Unit) = EP_NAME.forEachExtensionSafe {
      it.subscribeToAuthorizationStateEvents(disposable, listener)
    }
  }
}