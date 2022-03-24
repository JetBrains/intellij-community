// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.util.Disposer
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Test

class ServiceContainerTest {
  @Test
  fun `cyclic detection`() {
    val disposable = Disposer.newDisposable()
    com.intellij.openapi.diagnostic.DefaultLogger.disableStderrDumping(disposable)
    try {
      val componentManager = TestComponentManager()
      val pluginDescriptor = DefaultPluginDescriptor("test")
      componentManager.registerServiceInstance(ComponentManager::class.java, componentManager, pluginDescriptor)

      componentManager.registerService(C1::class.java, C1::class.java, pluginDescriptor, override = false)
      componentManager.registerService(C2::class.java, C2::class.java, pluginDescriptor, override = false)

      assertThatThrownBy {
        componentManager.getService(C1::class.java)
      }
        .hasMessageContaining("Cyclic service initialization")
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  fun `service as data class`() {
    val componentManager = TestComponentManager()
    componentManager.registerService(ServiceAsDataClass::class.java, ServiceAsDataClass::class.java, testPluginDescriptor, false)
    componentManager.getService(ServiceAsDataClass::class.java)
  }
}

private class C1(componentManager: ComponentManager) {
  init {
    componentManager.getService(C2::class.java)
  }
}

private class C2(componentManager: ComponentManager) {
  init {
    componentManager.getService(C1::class.java)
  }
}

private data class ServiceAsDataClass(var a: String? = null, var b: String? = null)