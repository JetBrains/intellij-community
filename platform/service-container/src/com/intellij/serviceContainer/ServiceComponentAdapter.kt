// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

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
) : BaseComponentAdapter(componentManager, pluginDescriptor, deferred, implementationClass) {
  override val implementationClassName: String
    get() = descriptor.implementation!!

  override fun isImplementationEqualsToInterface() = descriptor.serviceInterface == null || descriptor.serviceInterface == descriptor.implementation

  override fun getComponentKey(): String = descriptor.getInterface()

  override fun getActivityCategory(componentManager: ComponentManagerImpl) = componentManager.getActivityCategory(isExtension = false)

  override fun <T : Any> doCreateInstance(componentManager: ComponentManagerImpl, implementationClass: Class<T>): T {
    if (isDebugEnabled) {
      val app = componentManager.getApplication()
      if (app != null && app.isWriteAccessAllowed && !app.isUnitTestMode &&
          PersistentStateComponent::class.java.isAssignableFrom(implementationClass)) {
        LOG.warn(Throwable("Getting service from write-action leads to possible deadlock. Service implementation $implementationClassName"))
      }
    }
    return createAndInitialize(componentManager, implementationClass)
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