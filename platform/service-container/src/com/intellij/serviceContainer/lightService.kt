// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CompletableDeferred
import java.lang.reflect.Modifier

internal class LightServiceComponentAdapter(
  private val serviceClass: Class<*>,
  pluginDescriptor: PluginDescriptor,
  componentManager: ComponentManagerImpl,
  deferred: CompletableDeferred<Any> = CompletableDeferred(),
) : BaseComponentAdapter(componentManager = componentManager,
                         pluginDescriptor = pluginDescriptor,
                         deferred = deferred,
                         implementationClass = serviceClass) {
  override val implementationClassName: String
    get() = serviceClass.name

  override fun isImplementationEqualsToInterface(): Boolean = true

  override fun getComponentKey(): String = implementationClassName

  override fun getActivityCategory(componentManager: ComponentManagerImpl) = componentManager.getActivityCategory(isExtension = false)

  override fun <T : Any> doCreateInstance(componentManager: ComponentManagerImpl, implementationClass: Class<T>): T {
    val instance = componentManager.instantiateClass(implementationClass, pluginId)
    if (instance is Disposable) {
      Disposer.register(componentManager.serviceParentDisposable, instance)
    }
    componentManager.initializeComponent(component = instance, serviceDescriptor = null, pluginId = pluginId)
    return instance
  }

  override fun toString() = "LightServiceComponentAdapter(serviceClass=${implementationClassName}, pluginDescriptor=$pluginDescriptor)"
}

internal fun isLightService(serviceClass: Class<*>): Boolean {
  return Modifier.isFinal(serviceClass.modifiers) && serviceClass.isAnnotationPresent(Service::class.java)
}