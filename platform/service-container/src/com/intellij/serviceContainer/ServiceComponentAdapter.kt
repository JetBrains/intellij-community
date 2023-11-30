// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CompletableDeferred

private val isDebugEnabled = LOG.isDebugEnabled

internal class ServiceComponentAdapter(
  @JvmField val descriptor: ServiceDescriptor,
  pluginDescriptor: PluginDescriptor,
  componentManager: ComponentManagerImpl,
  implementationClass: Class<*>? = null,
  deferred: CompletableDeferred<Any> = CompletableDeferred()
) : BaseComponentAdapter(componentManager = componentManager,
                         pluginDescriptor = pluginDescriptor,
                         deferred = deferred,
                         implementationClass = implementationClass) {
  override val implementationClassName: String
    get() = getServiceImplementation(descriptor, componentManager)

  override fun isImplementationEqualsToInterface(): Boolean {
    val serviceInterface = descriptor.serviceInterface ?: return true
    return serviceInterface == getServiceImplementation(descriptor, componentManager)
  }

  override fun getComponentKey(): String = getServiceInterface(descriptor, componentManager)

  override fun getActivityCategory(componentManager: ComponentManagerImpl) = componentManager.getActivityCategory(isExtension = false)

  override fun <T : Any> doCreateInstance(componentManager: ComponentManagerImpl, implementationClass: Class<T>): T {
    if (isDebugEnabled) {
      val app = componentManager.getApplication()
      if (app != null && app.isWriteAccessAllowed && !app.isUnitTestMode &&
          PersistentStateComponent::class.java.isAssignableFrom(implementationClass)) {
        LOG.warn(Throwable("Getting service from write-action leads to possible deadlock. Service implementation $implementationClassName"))
      }
    }

    val instance = if (pluginId == PluginManagerCore.CORE_ID) {
      componentManager.instantiateClass(implementationClass, pluginId)
    }
    else {
      componentManager.instantiateClassWithConstructorInjection(implementationClass, componentKey, pluginId)
    }

    if (instance is Disposable) {
      Disposer.register(componentManager.serviceParentDisposable, instance)
    }
    componentManager.initializeComponent(instance, descriptor, pluginId)
    return instance
  }

  override fun toString() = "ServiceAdapter(descriptor=$descriptor, pluginDescriptor=$pluginDescriptor)"
}

internal fun getServiceInterface(descriptor: ServiceDescriptor, componentManager: ComponentManagerImpl): String {
  return descriptor.serviceInterface ?: getServiceImplementation(descriptor, componentManager)
}

internal fun getServiceImplementation(descriptor: ServiceDescriptor, componentManager: ComponentManagerImpl): String {
  return descriptor.testServiceImplementation?.takeIf { componentManager.getApplication()!!.isUnitTestMode }
         ?: descriptor.headlessImplementation?.takeIf { componentManager.getApplication()!!.isHeadlessEnvironment }
         ?: descriptor.serviceImplementation
}