// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ServiceContainerUtil")
package com.intellij.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.serviceContainer.PlatformComponentManagerImpl

fun <T : Any> ComponentManager.registerServiceInstance(serviceInterface: Class<T>, instance: T) {
  (this as PlatformComponentManagerImpl).registerServiceInstance(serviceInterface, instance, DefaultPluginDescriptor("test"))
}

fun <T : Any> ComponentManager.replaceService(serviceInterface: Class<T>, instance: T, parentDisposable: Disposable) {
  (this as PlatformComponentManagerImpl).replaceServiceInstance(serviceInterface, instance, parentDisposable)
}