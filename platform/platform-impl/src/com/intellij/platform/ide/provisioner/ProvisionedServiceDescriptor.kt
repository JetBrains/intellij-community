package com.intellij.platform.ide.provisioner

import kotlinx.coroutines.flow.Flow

/**
 * Descriptor for a (potentially provisioned) piece of IDE functionality.
 */
interface ProvisionedServiceDescriptor {
  /** Unique identifier for accessing the service endpoint using [ProvisionedServiceRegistry.getServiceById]. */
  val id: String

  /**
   * The state of whether the service is provisioned (with its configuration in that case) or not.
   *
   * Note that it may take some time for the initial value to become available in the flow
   * while the configuration is still loading.
   */
  val configurationFlow: Flow<ProvisionedServiceConfigurationResult>
}
