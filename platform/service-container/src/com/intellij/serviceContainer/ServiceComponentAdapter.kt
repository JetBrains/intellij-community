// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.diagnostic.ParallelActivity
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.storage.HeavyProcessLatch
import com.intellij.util.pico.AssignableToComponentAdapter
import org.picocontainer.PicoContainer

private val LOG = logger<ServiceComponentAdapter>()

internal class ServiceComponentAdapter(val descriptor: ServiceDescriptor,
                                       val pluginDescriptor: PluginDescriptor,
                                       private val componentManager: PlatformComponentManagerImpl,
                                       private var implementationClass: Class<*>? = null,
                                       initializedInstance: Any? = null) : BaseComponentAdapter(initializedInstance), AssignableToComponentAdapter {
  override val pluginId: PluginId
    get() = pluginDescriptor.pluginId

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

  override fun getComponentInstance(container: PicoContainer): Any? = getInstance(componentManager, createIfNeeded = true)

  override fun <T : Any> doCreateInstance(componentManager: PlatformComponentManagerImpl, indicator: ProgressIndicator?): T {
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
        return createAndInitialize(componentManager)
      }
      else {
        var instance: T? = null
        ProgressManager.getInstance().executeNonCancelableSection {
          instance = createAndInitialize(componentManager)
        }
        return instance!!
      }
    }
  }

  private fun <T : Any> createAndInitialize(componentManager: PlatformComponentManagerImpl): T {
    val startTime = StartUpMeasurer.getCurrentTime()
    @Suppress("UNCHECKED_CAST")
    val implementationClass = getImplementationClass() as Class<T>
    val instance = componentManager.instantiateClassWithConstructorInjection(implementationClass, componentKey, pluginId)
    if (instance is Disposable) {
      Disposer.register(componentManager, instance)
    }

    componentManager.initializeComponent(instance, descriptor)
    ParallelActivity.SERVICE.record(startTime, implementationClass, componentManager.getActivityLevel(), pluginId.idString)
    return instance
  }

  override fun getAssignableToClassName(): String = descriptor.getInterface()

  override fun toString() = "ServiceComponentAdapter(descriptor=$descriptor, pluginDescriptor=$pluginDescriptor)"
}