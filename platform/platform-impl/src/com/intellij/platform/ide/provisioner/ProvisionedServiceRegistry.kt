package com.intellij.platform.ide.provisioner

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service

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
    fun getInstance(): ProvisionedServiceRegistry = ApplicationManager.getApplication().service()
  }
}

internal class DefaultProvisionedServiceRegistry : ProvisionedServiceRegistry {
  override fun getServiceById(id: String): ProvisionedServiceDescriptor? = null
}
