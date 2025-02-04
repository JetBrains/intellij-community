package com.intellij.platform.ide.provisioner.endpoint

import kotlinx.coroutines.flow.Flow

/**
 * Describes a server endpoint for the provisioned service.
 */
interface ServiceEndpoint {
  /** The URL of the service endpoint. */
  val serverUrl: String

  /**
   * The current [token][AuthTokenResult] required to access the [server][serverUrl].
   * The implementation is responsible for refreshing the token, so that the latest token value
   * available in the flow is always usable (unless there's an [AuthTokenResult.Failure]).
   *
   * Note that an [AuthTokenResult.Success] doesn't guarantee that the token is going to stay
   * valid up until its expiration time.
   * The client code should still be able to handle an authorization error properly,
   * and the [reportAuthFailure] method can be helpful in facilitating that.
   */
  val authTokenFlow: Flow<AuthTokenResult>

  /**
   * The client is advised to call this method as part of graceful error handling if a request
   * to the endpoint fails because of an authentication failure ("401 Unauthorized").
   *
   * The token may become invalid due to an external change; for instance, the SSO provider
   * may forcibly log the user out. An event like that may go unnoticed by the provisioner,
   * and the new state may not be reflected in the [authTokenFlow] automatically.
   *
   * By calling this method, the client notifies the provisioner that the token is no more
   * usable. The provisioner then makes the best effort to revalidate the token, which may
   * hopefully result in a new token state (probably an [AuthTokenResult.Failure.LoginRequired])
   * being pushed eventually through the [authTokenFlow].
   *
   * Note that the contract of this method should indeed be only treated as "best effort".
   * The client must not rely on a new token state becoming available in the [authTokenFlow]
   * right after calling this method, and should design the interaction with the user accordingly.
   * In this sense, the default "noop" way of how the provisioner could implement this method
   * is perfectly fine.
   */
  fun reportAuthFailure(authToken: AuthToken) {}
}
