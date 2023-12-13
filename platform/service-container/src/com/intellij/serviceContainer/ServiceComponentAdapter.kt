// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.openapi.components.ServiceDescriptor

internal fun getServiceInterface(descriptor: ServiceDescriptor, componentManager: ComponentManagerImpl): String {
  return descriptor.serviceInterface ?: getServiceImplementation(descriptor, componentManager)
}

internal fun getServiceImplementation(descriptor: ServiceDescriptor, componentManager: ComponentManagerImpl): String {
  return descriptor.testServiceImplementation?.takeIf { componentManager.getApplication()!!.isUnitTestMode }
         ?: descriptor.headlessImplementation?.takeIf { componentManager.getApplication()!!.isHeadlessEnvironment }
         ?: descriptor.serviceImplementation
}