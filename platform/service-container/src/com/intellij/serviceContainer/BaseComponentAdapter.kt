// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.diagnostic.LoadingPhase
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import org.picocontainer.ComponentAdapter
import org.picocontainer.PicoContainer
import org.picocontainer.PicoVisitor

internal abstract class BaseComponentAdapter(@field:Volatile protected var initializedInstance: Any?) : ComponentAdapter {
  companion object {
    private val LOG = logger<BaseComponentAdapter>()
  }

  private var initializing = false

  final override fun verify(container: PicoContainer) {}

  final override fun accept(visitor: PicoVisitor) {
    visitor.visitComponentAdapter(this)
  }

  protected abstract val pluginId: PluginId

  @Suppress("UNCHECKED_CAST")
  fun <T : Any> getInstance(createIfNeeded: Boolean, componentManager: PlatformComponentManagerImpl, indicator: ProgressIndicator? = null): T? {
    var instance = initializedInstance as T?
    if (instance != null || !createIfNeeded) {
      return instance
    }

    LoadingPhase.COMPONENT_REGISTERED.assertAtLeast()

    synchronized(this) {
      instance = initializedInstance as T?
      if (instance != null) {
        return instance
      }

      if (initializing) {
        LOG.error(PluginException("Cyclic service initialization: ${toString()}", pluginId))
      }

      try {
        initializing = true
        instance = doCreateInstance(componentManager, indicator)
        initializedInstance = instance
        return instance
      }
      finally {
        initializing = false
      }
    }
  }

  protected abstract fun <T : Any> doCreateInstance(componentManager: PlatformComponentManagerImpl, indicator: ProgressIndicator?): T
}

internal abstract class InstantiatingComponentAdapter(private val componentKey: Class<*>, private val componentImplementation: Class<*>) : BaseComponentAdapter(null) {
  final override fun getComponentKey() = componentKey

  final override fun getComponentImplementation() = componentImplementation

  protected fun <T : Any> createComponentInstance(componentManager: PlatformComponentManagerImpl): T {
    return instantiateUsingPicoContainer(componentImplementation, componentKey, componentManager, ConstructorParameterResolver.INSTANCE)
  }
}