// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.diagnostic.LoadingPhase
import com.intellij.diagnostic.ParallelActivity
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.storage.HeavyProcessLatch
import com.intellij.util.pico.AssignableToComponentAdapter
import org.picocontainer.PicoContainer
import org.picocontainer.PicoVisitor

private val LOG = logger<ServiceComponentAdapter>()

internal class ServiceComponentAdapter(val descriptor: ServiceDescriptor,
                                       val pluginDescriptor: PluginDescriptor,
                                       private val componentManager: PlatformComponentManagerImpl,
                                       private var implementationClass: Class<*>? = null,
                                       @field:Volatile private var initializedInstance: Any? = null) : AssignableToComponentAdapter {
  private var initializing = false

  @Synchronized
  fun isImplementationClassResolved() = implementationClass != null

  @Synchronized
  fun getImplementationClass(): Class<*> {
    var result = implementationClass
    if (result == null) {
      val implClass: Class<*> = try {
        Class.forName(descriptor.implementation, true, pluginDescriptor.pluginClassLoader)
      }
      catch (e: ClassNotFoundException) {
        throw PluginException("Failed to load class: $descriptor", e, pluginDescriptor.pluginId)
      }

      result = implClass
      implementationClass = result
    }
    return result
  }

  override fun getComponentKey(): String = descriptor.getInterface()

  override fun getComponentImplementation() = getImplementationClass()

  override fun getComponentInstance(container: PicoContainer): Any? = getInstance(true, componentManager)

  fun <T : Any> getInstance(createIfNeeded: Boolean, componentManager: PlatformComponentManagerImpl): T? {
    var instance = initializedInstance
    if (instance != null || !createIfNeeded) {
      @Suppress("UNCHECKED_CAST")
      return instance as T?
    }

    LoadingPhase.COMPONENT_REGISTERED.assertAtLeast()

    synchronized(this) {
      instance = initializedInstance
      if (instance != null) {
        @Suppress("UNCHECKED_CAST")
        return instance as T
      }

      if (initializing) {
        // see https://youtrack.jetbrains.com/issue/IDEA-220429
        LOG.error(PluginException("Cyclic service initialization: ${toString()}", pluginDescriptor.pluginId))
      }

      try {
        initializing = true

        val implementation = descriptor.implementation
        if (LOG.isDebugEnabled) {
          val app = componentManager.getApplication()
          if (app != null && app.isWriteAccessAllowed && !app.isUnitTestMode &&
              PersistentStateComponent::class.java.isAssignableFrom(getImplementationClass())) {
            LOG.warn(Throwable("Getting service from write-action leads to possible deadlock. Service implementation ${implementation!!}"))
          }
        }

        // heavy to prevent storages from flushing and blocking FS
        HeavyProcessLatch.INSTANCE.processStarted("Creating service: $implementation").use {
          if (ProgressManager.getGlobalProgressIndicator() == null) {
            @Suppress("UNCHECKED_CAST")
            return createAndInitialize(componentManager) as T
          }
          else {
            ProgressManager.getInstance().executeNonCancelableSection {
              createAndInitialize(componentManager)
            }
            @Suppress("UNCHECKED_CAST")
            return initializedInstance as T
          }
        }
      }
      finally {
        initializing = false
      }
    }
  }

  private fun createAndInitialize(componentManager: PlatformComponentManagerImpl): Any {
    val startTime = StartUpMeasurer.getCurrentTime()
    val implementationClass = getImplementationClass()
    val instance = componentManager.instantiateClassWithConstructorInjection(implementationClass, componentKey, pluginDescriptor.pluginId)
    if (instance is Disposable) {
      Disposer.register(componentManager, instance)
    }

    componentManager.initializeComponent(instance, descriptor)
    ParallelActivity.SERVICE.record(startTime, implementationClass, componentManager.getActivityLevel(), pluginDescriptor.pluginId.idString)
    initializedInstance = instance
    return instance
  }

  override fun verify(container: PicoContainer) {}

  override fun accept(visitor: PicoVisitor) {
    visitor.visitComponentAdapter(this)
  }

  override fun getAssignableToClassName(): String = descriptor.getInterface()

  override fun toString() = "ServiceComponentAdapter(descriptor=$descriptor, pluginDescriptor=$pluginDescriptor)"

  fun <T> replaceInstance(instance: T, parentDisposable: Disposable) {
    synchronized(this) {
      val old = initializedInstance
      initializedInstance = instance

      Disposer.register(parentDisposable, Disposable {
        synchronized(this) {
          if (initializedInstance === instance && instance is Disposable && !Disposer.isDisposed(instance)) {
            Disposer.dispose(instance)
          }
          initializedInstance = old
        }
      })
    }
  }
}