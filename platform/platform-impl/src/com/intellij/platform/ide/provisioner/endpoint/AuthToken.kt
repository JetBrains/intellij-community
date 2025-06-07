package com.intellij.platform.ide.provisioner.endpoint

data class AuthToken(
  /**
   * The map of HTTP request headers required for authenticating with the corresponding [ServiceEndpoint].
   * Typically, it contains at least the `"Authorization"` credentials, but that's not guaranteed.
   */
  val requestHeaders: Map<String, String>,
) {
  @Deprecated("For backward compatibility, until TBE plugin is updated")
  @Suppress("unused")
  constructor(
    tokenValue: String,
    tokenSchema: String,
    additionalHeaders: Map<String, String>,
  ) : this(requestHeaders = mapOf("Authorization" to "${tokenSchema} ${tokenValue}") + additionalHeaders)
}

sealed interface AuthTokenResult {
  data class Success(val token: AuthToken) : AuthTokenResult

  sealed interface Failure : AuthTokenResult {
    val message: String

    data class Timeout(override val message: String) : Failure
    data class NetworkError(override val message: String) : Failure
    data class LoginRequired(override val message: String) : Failure
    data class ValidationError(override val message: String) : Failure
    data class GenericError(override val message: String, val cause: Throwable? = null) : Failure
  }
}
