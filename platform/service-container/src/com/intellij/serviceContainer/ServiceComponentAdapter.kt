// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer

internal class ServiceComponentAdapter(val descriptor: ServiceDescriptor,
                                       pluginDescriptor: PluginDescriptor,
                                       componentManager: ComponentManagerImpl,
                                       implementationClass: Class<*>? = null,
                                       initializedInstance: Any? = null) : BaseComponentAdapter(componentManager, pluginDescriptor, initializedInstance, implementationClass) {
  companion object {
    private val isDebugEnabled = LOG.isDebugEnabled
  }

  override val implementationClassName: String
    get() = descriptor.implementation!!

  override fun isImplementationEqualsToInterface() = descriptor.serviceInterface == null || descriptor.serviceInterface == descriptor.implementation

  override fun getComponentKey(): String = descriptor.getInterface()

  override fun getActivityCategory(componentManager: ComponentManagerImpl) = componentManager.getActivityCategory(isExtension = false)

  override fun <T : Any> doCreateInstance(componentManager: ComponentManagerImpl, implementationClass: Class<T>, indicator: ProgressIndicator?): T {
    if (isDebugEnabled) {
      val app = componentManager.getApplication()
      if (app != null && app.isWriteAccessAllowed && !app.isUnitTestMode &&
          PersistentStateComponent::class.java.isAssignableFrom(implementationClass)) {
        LOG.warn(Throwable("Getting service from write-action leads to possible deadlock. Service implementation $implementationClassName"))
      }
    }

    val progressManager = if (indicator == null) null else ProgressManager.getInstance()
    if (progressManager == null || progressManager.isInNonCancelableSection) {
      return createAndInitialize(componentManager, implementationClass)
    }
    else {
      return progressManager.computeInNonCancelableSection<T, Exception> {
        createAndInitialize(componentManager, implementationClass)
      }
    }
  }

  private fun <T : Any> createAndInitialize(componentManager: ComponentManagerImpl, implementationClass: Class<T>): T {
    val instance = componentManager.instantiateClassWithConstructorInjection(implementationClass, componentKey, pluginId)
    if (instance is Disposable) {
      Disposer.register(componentManager.serviceParentDisposable, instance)
    }
    componentManager.initializeComponent(instance, descriptor, pluginId)
    return instance
  }

  override fun toString() = "ServiceAdapter(descriptor=$descriptor, pluginDescriptor=$pluginDescriptor)"
}