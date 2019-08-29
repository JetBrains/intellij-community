// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ServiceContainerUtil")
package com.intellij.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.impl.ComponentManagerImpl
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.serviceContainer.PlatformComponentManagerImpl
import org.jetbrains.annotations.TestOnly

@TestOnly
fun <T : Any> ComponentManager.registerServiceInstance(serviceInterface: Class<T>, instance: T) {
  (this as PlatformComponentManagerImpl).registerServiceInstance(serviceInterface, instance, DefaultPluginDescriptor("test"))
}

@TestOnly
fun <T : Any> ComponentManager.replaceService(serviceInterface: Class<T>, instance: T, parentDisposable: Disposable) {
  (this as PlatformComponentManagerImpl).replaceServiceInstance(serviceInterface, instance, parentDisposable)
}

@TestOnly
fun <T> ComponentManager.registerComponentInstance(componentInterface: Class<T>, instance: T): T {
  return (this as ComponentManagerImpl).registerComponentInstance(componentInterface, instance)
}

@TestOnly
@JvmOverloads
fun ComponentManager.registerComponentImplementation(componentInterface: Class<*>, componentImplementation: Class<*>, shouldBeRegistered: Boolean = false) {
  (this as PlatformComponentManagerImpl).registerComponentImplementation(componentInterface, componentImplementation, shouldBeRegistered)
}

@TestOnly
fun <T> ComponentManager.registerExtension(name: BaseExtensionPointName, instance: T, parentDisposable: Disposable) {
  extensionArea.getExtensionPoint<T>(name.name).registerExtension(instance, parentDisposable)
}

@TestOnly
fun ComponentManager.getServiceImplementationClassNames(prefix: String): List<String> {
  return (this as PlatformComponentManagerImpl).getServiceImplementationClassNames(prefix)
}