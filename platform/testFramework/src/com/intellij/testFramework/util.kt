// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import com.intellij.openapi.components.ComponentManager
import org.picocontainer.MutablePicoContainer

fun <T> ComponentManager.registerServiceInstance(interfaceClass: Class<T>, instance: T) {
  val picoContainer = picoContainer as MutablePicoContainer
  val key = interfaceClass.name
  picoContainer.unregisterComponent(key)
  picoContainer.registerComponentInstance(key, instance)
}