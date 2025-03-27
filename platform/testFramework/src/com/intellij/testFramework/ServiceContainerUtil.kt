// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ServiceContainerUtil")
package com.intellij.testFramework

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.MessageBusOwner
import com.intellij.util.messages.impl.PluginListenerDescriptor
import org.jetbrains.annotations.TestOnly

private val testDescriptor by lazy { DefaultPluginDescriptor("test") }

@TestOnly
fun <T : Any> ComponentManager.registerServiceInstance(serviceInterface: Class<T>, instance: T) {
  (this as ComponentManagerEx).registerServiceInstance(serviceInterface, instance, testDescriptor)
}

/**
 * Unregister service specified by [serviceInterface] if it was registered;
 * throws [IllegalStateException] if the service was not registered.
 */
@TestOnly
fun ComponentManager.unregisterService(serviceInterface: Class<*>) {
  (this as ComponentManagerEx).unregisterService(serviceInterface)
}

/**
 * Register a new service or replace an existing service with a specified instance for testing purposes.
 * Registration will be rolled back when parentDisposable is disposed. In most of the cases,
 * [com.intellij.testFramework.UsefulTestCase.getTestRootDisposable] should be specified.
 */
@TestOnly
fun <T : Any> ComponentManager.registerOrReplaceServiceInstance(serviceInterface: Class<T>, instance: T, parentDisposable: Disposable) {
  val previous = this.getService(serviceInterface)
  if (previous != null) {
    replaceService(serviceInterface, instance, parentDisposable)
  }
  else {
    val impl = (this as ComponentManagerEx)
    impl.registerServiceInstance(serviceInterface, instance, testDescriptor)
    if (instance is Disposable) {
      Disposer.register(parentDisposable, instance)
    }
    else {
      Disposer.register(parentDisposable) {
        impl.unregisterComponent(serviceInterface)
      }
    }
  }
}

@TestOnly
fun <T : Any> ComponentManager.replaceService(serviceInterface: Class<T>, instance: T, parentDisposable: Disposable) {
  (this as ComponentManagerEx).replaceServiceInstance(serviceInterface, instance, parentDisposable)
}

@TestOnly
fun <T : Any> ComponentManager.registerComponentInstance(componentInterface: Class<T>, instance: T, parentDisposable: Disposable?) {
  (this as ComponentManagerEx).replaceComponentInstance(componentInterface, instance, parentDisposable)
}

@TestOnly
@JvmOverloads
fun ComponentManager.registerComponentImplementation(key: Class<*>, implementation: Class<*>, shouldBeRegistered: Boolean = false) {
  (this as ComponentManagerEx).registerComponentImplementation(key, implementation, shouldBeRegistered)
}

@TestOnly
fun <T : Any> ComponentManager.registerExtension(name: BaseExtensionPointName<*>, instance: T, parentDisposable: Disposable) {
  extensionArea.getExtensionPoint<T>(name.name).registerExtension(instance, parentDisposable)
}

@TestOnly
fun ComponentManager.getServiceImplementationClassNames(prefix: String): List<String> {
  val result = ArrayList<String>()
  processAllServiceDescriptors(this) { serviceDescriptor ->
    val implementation = serviceDescriptor.implementation ?: return@processAllServiceDescriptors
    if (implementation.startsWith(prefix)) {
      result.add(implementation)
    }
  }
  return result
}

fun processAllServiceDescriptors(componentManager: ComponentManager, consumer: (ServiceDescriptor) -> Unit) {
  for (plugin in PluginManagerCore.loadedPlugins) {
    val pluginDescriptor = plugin as IdeaPluginDescriptorImpl
    val containerDescriptor = when (componentManager) {
      is Application -> pluginDescriptor.appContainerDescriptor
      is Project -> pluginDescriptor.projectContainerDescriptor
      else -> pluginDescriptor.moduleContainerDescriptor
    }
    containerDescriptor.services.forEach {
      if ((componentManager as? ComponentManagerEx)?.isServiceSuitable(it) != false && (it.os == null || it.os.isSuitableForOs())) {
        consumer(it)
      }
    }
  }
}

fun createSimpleMessageBusOwner(owner: String): MessageBusOwner {
  return object : MessageBusOwner {
    override fun createListener(descriptor: PluginListenerDescriptor) = throw UnsupportedOperationException()

    override fun isDisposed() = false

    override fun toString() = owner
  }
}