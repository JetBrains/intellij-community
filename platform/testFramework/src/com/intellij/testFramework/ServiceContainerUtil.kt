// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ServiceContainerUtil")
package com.intellij.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.util.messages.ListenerDescriptor
import com.intellij.util.messages.MessageBusOwner
import org.jetbrains.annotations.TestOnly

private val testDescriptor by lazy { DefaultPluginDescriptor("test") }

@TestOnly
fun <T : Any> ComponentManager.registerServiceInstance(serviceInterface: Class<T>, instance: T) {
  (this as ComponentManagerImpl).registerServiceInstance(serviceInterface, instance, testDescriptor)
}

@TestOnly
fun <T : Any> ComponentManager.replaceService(serviceInterface: Class<T>, instance: T, parentDisposable: Disposable) {
  (this as ComponentManagerImpl).replaceServiceInstance(serviceInterface, instance, parentDisposable)
}

/**
 * Returns old instance.
 */
@TestOnly
fun <T : Any> ComponentManager.registerComponentInstance(componentInterface: Class<T>, instance: T, parentDisposable: Disposable?): T? {
  return (this as ComponentManagerImpl).replaceComponentInstance(componentInterface, instance, parentDisposable)
}

@Suppress("DeprecatedCallableAddReplaceWith")
@TestOnly
@Deprecated("Pass parentDisposable")
fun <T : Any> ComponentManager.registerComponentInstance(componentInterface: Class<T>, instance: T): T? {
  return (this as ComponentManagerImpl).replaceComponentInstance(componentInterface, instance, null)
}

@TestOnly
@JvmOverloads
fun ComponentManager.registerComponentImplementation(componentInterface: Class<*>, componentImplementation: Class<*>, shouldBeRegistered: Boolean = false) {
  (this as ComponentManagerImpl).registerComponentImplementation(componentInterface, componentImplementation, shouldBeRegistered)
}

@TestOnly
fun <T : Any> ComponentManager.registerExtension(name: BaseExtensionPointName<*>, instance: T, parentDisposable: Disposable) {
  extensionArea.getExtensionPoint<T>(name.name).registerExtension(instance, parentDisposable)
}

@TestOnly
fun ComponentManager.getServiceImplementationClassNames(prefix: String): List<String> {
  return (this as ComponentManagerImpl).getServiceImplementationClassNames(prefix)
}

fun createSimpleMessageBusOwner(owner: String): MessageBusOwner {
  return object : MessageBusOwner {
    override fun createListener(descriptor: ListenerDescriptor) = throw UnsupportedOperationException()

    override fun isDisposed() = false

    override fun toString() = owner
  }
}