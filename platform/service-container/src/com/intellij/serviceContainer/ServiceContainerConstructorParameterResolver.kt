// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import java.lang.reflect.Constructor

internal class ServiceContainerConstructorParameterResolver(private val isExtensionSupported: Boolean) : ConstructorParameterResolver() {
  override fun isResolvable(componentManager: PlatformComponentManagerImpl,
                            requestorKey: Any,
                            requestorClass: Class<*>,
                            requestorConstructor: Constructor<*>,
                            expectedType: Class<*>): Boolean {
    if (isLightService(expectedType) || super.isResolvable(componentManager, requestorKey, requestorClass, requestorConstructor, expectedType)) {
      return true
    }
    return isExtensionSupported && componentManager.extensionArea.findExtensionByClass(expectedType) != null
  }

  override fun resolveInstance(componentManager: PlatformComponentManagerImpl,
                               requestorKey: Any,
                               requestorClass: Class<*>,
                               requestorConstructor: Constructor<*>,
                               expectedType: Class<*>): Any? {
    if (isLightService(expectedType)) {
      return componentManager.getLightService(expectedType, true)
    }
    return super.resolveInstance(componentManager, requestorKey, requestorClass, requestorConstructor, expectedType)
  }

  override fun handleUnsatisfiedDependency(componentManager: PlatformComponentManagerImpl, requestorClass: Class<*>, expectedType: Class<*>): Any? {
    if (isExtensionSupported) {
      val extension = componentManager.extensionArea.findExtensionByClass(expectedType)
      if (extension != null) {
        LOG.warn("Do not use constructor injection to get extension instance (requestorClass=${requestorClass.name}, extensionClass=${expectedType.name})")
      }
      return extension
    }
    return super.handleUnsatisfiedDependency(componentManager, requestorClass, expectedType)
  }
}