// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.diagnostic.ActivityCategory
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CompletableDeferred
import org.picocontainer.ComponentAdapter

internal class MyComponentAdapter(private val componentKey: Class<*>,
                                  override val implementationClassName: String,
                                  pluginDescriptor: PluginDescriptor,
                                  componentManager: ComponentManagerImpl,
                                  deferred: CompletableDeferred<Any>,
                                  implementationClass: Class<*>?) : BaseComponentAdapter(componentManager, pluginDescriptor, deferred,
                                                                                         implementationClass) {
  override fun getComponentKey() = componentKey

  override fun isImplementationEqualsToInterface() = componentKey.name == implementationClassName

  override fun getActivityCategory(componentManager: ComponentManagerImpl): ActivityCategory? {
    if (componentManager.activityNamePrefix() == null) {
      return null
    }

    val parent = componentManager.parent
    return when {
      parent == null -> ActivityCategory.APP_COMPONENT
      parent.parent == null -> ActivityCategory.PROJECT_COMPONENT
      else -> ActivityCategory.MODULE_COMPONENT
    }
  }

  override fun <T : Any> doCreateInstance(componentManager: ComponentManagerImpl, implementationClass: Class<T>): T {
    val instance = componentManager.instantiateClassWithConstructorInjection(implementationClass, componentKey, pluginId)
    if (instance is Disposable) {
      Disposer.register(componentManager.serviceParentDisposable, instance)
    }

    componentManager.initializeComponent(instance, serviceDescriptor = null, pluginId = pluginId)
    @Suppress("DEPRECATION")
    if (instance is com.intellij.openapi.components.BaseComponent) {
      @Suppress("DEPRECATION")
      instance.initComponent()
      if (instance !is Disposable) {
        @Suppress("ObjectLiteralToLambda")
        Disposer.register(componentManager.serviceParentDisposable, object : Disposable {
          override fun dispose() {
            @Suppress("DEPRECATION")
            instance.disposeComponent()
          }
        })
      }
    }
    return instance
  }

  override fun toString() = "ComponentAdapter(key=${getComponentKey()}, implementation=${implementationClassName}, plugin=$pluginId)"

  // used in LinkedHashSetWrapper
  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    return other is ComponentAdapter && componentKey == other.componentKey
  }

  override fun hashCode() = componentKey.hashCode()
}