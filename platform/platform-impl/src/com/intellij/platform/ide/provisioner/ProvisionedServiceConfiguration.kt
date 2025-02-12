package com.intellij.platform.ide.provisioner

import com.intellij.platform.ide.provisioner.endpoint.ServiceEndpoint

data class ProvisionedServiceConfiguration(
  /** Generic Key-Value map of service-specific properties. */
  private val properties: Map<String, String>,

  /** Endpoint descriptor in case the service involves a remote server. */
  val endpoint: ServiceEndpoint?,
) {
  operator fun get(key: String): String? = properties[key]
}

sealed interface ProvisionedServiceConfigurationResult {
  /**
   * Represents the successfully loaded state.
   */
  sealed interface Success : ProvisionedServiceConfigurationResult {
    data class ServiceProvisioned(val configuration: ProvisionedServiceConfiguration) : Success
    data object ServiceNotProvisioned : Success
  }

  /**
   * Denotes that the provisioner could not load the configuration of the service due to an error.
   * Depending on the particular service, there may be different ways of treating this state.
   * For example, the client may fall back to some predefined default configuration,
   * or it may choose to prohibit the use of the corresponding IDE functionality altogether
   * until a proper configuration becomes available.
   */
  sealed interface Failure : ProvisionedServiceConfigurationResult {
    val message: String

    data class LoginRequired(override val message: String) : Failure
    data class GenericError(override val message: String, val cause: Throwable? = null) : Failure
  }
}
