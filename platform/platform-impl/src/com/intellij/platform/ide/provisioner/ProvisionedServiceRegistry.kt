package com.intellij.platform.ide.provisioner

import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName

interface ProvisionedServiceRegistry {
  /**
   * Retrieves a [ProvisionedServiceDescriptor] by its [ProvisionedServiceDescriptor.id],
   * or null if the provisioner doesn't recognize the ID.
   * Note that a non-null result only means that the installed version of the provisioner plugin
   * is aware and support the requested service; it doesn't mean that the service is available and/or enabled -
   * this is what [ProvisionedServiceDescriptor.configurationFlow] is for.
   */
  fun getServiceById(id: String): ProvisionedServiceDescriptor?

  companion object {
    @JvmField
    val EP: ExtensionPointName<ProvisionedServiceRegistry> = ExtensionPointName("com.intellij.provisionedServiceRegistry")

    @JvmStatic
    fun getInstance(): ProvisionedServiceRegistry = CompositeProvisionedServiceRegistry
  }
}

internal object CompositeProvisionedServiceRegistry : ProvisionedServiceRegistry {
  override fun getServiceById(id: String): ProvisionedServiceDescriptor? {
    return ProvisionedServiceRegistry.EP.computeSafeIfAny { it.getServiceById(id) }
           ?: service<ProvisionedServiceRegistry>().getServiceById(id)
  }
}

internal class DefaultProvisionedServiceRegistry : ProvisionedServiceRegistry {
  override fun getServiceById(id: String): ProvisionedServiceDescriptor? = null
}
