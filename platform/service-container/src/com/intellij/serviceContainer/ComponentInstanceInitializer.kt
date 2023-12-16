// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.platform.instanceContainer.internal.InstanceInitializer
import kotlinx.coroutines.CoroutineScope

internal abstract class ComponentInstanceInitializer(
  val componentManager: ComponentManagerImpl,
  val pluginId: PluginId,
  val interfaceClass: Class<*>,
) : InstanceInitializer {

  override suspend fun createInstance(parentScope: CoroutineScope, instanceClass: Class<*>): Any {
    try {
      val instance = instantiateWithContainer(componentManager.dependencyResolver, parentScope, instanceClass, pluginId)
      if (instance is Disposable) {
        Disposer.register(componentManager.serviceParentDisposable, instance)
      }
      componentManager.initializeService(instance, serviceDescriptor = null, pluginId = pluginId)
      @Suppress("DEPRECATION")
      if (instance is com.intellij.openapi.components.BaseComponent) {
        @Suppress("DEPRECATION")
        instance.initComponent()
        if (instance !is Disposable) {
          @Suppress("ObjectLiteralToLambda")
          (Disposer.register(componentManager.serviceParentDisposable, object : Disposable {
            override fun dispose() {
              @Suppress("DEPRECATION")
              instance.disposeComponent()
            }
          }))
        }
      }
      return instance
    }
    catch (t: Throwable) {
      handleComponentError(t, interfaceClass.name, pluginId)
      throw t
    }
  }
}

internal class ComponentDescriptorInstanceInitializer(
  componentManager: ComponentManagerImpl,
  private val pd: PluginDescriptor,
  interfaceClass: Class<*>,
  override val instanceClassName: String,
) : ComponentInstanceInitializer(componentManager, pd.pluginId, interfaceClass) {

  override fun loadInstanceClass(keyClass: Class<*>?): Class<*> {
    if (keyClass != null && (interfaceClass.name == instanceClassName)) {
      // avoid classloading
      return keyClass
    }
    else {
      return componentManager.loadClass<Any>(instanceClassName, pd)
    }
  }
}

internal class ComponentClassInstanceInitializer(
  componentManager: ComponentManagerImpl,
  pluginId: PluginId,
  interfaceClass: Class<*>,
  private val instanceClass: Class<*>,
) : ComponentInstanceInitializer(componentManager, pluginId, interfaceClass) {

  override val instanceClassName: String get() = instanceClass.name

  override fun loadInstanceClass(keyClass: Class<*>?): Class<*> = instanceClass
}
