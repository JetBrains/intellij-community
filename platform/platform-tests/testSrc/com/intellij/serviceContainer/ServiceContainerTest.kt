// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.util.Disposer
import org.assertj.core.api.Assertions.assertThat
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
      try {
        componentManager.getService(C1::class.java)
      }
      catch (e: Throwable) {
        val stringBuilder = StringBuilder()
        var cause = e
        while (true) {
          stringBuilder.append(cause.javaClass.simpleName).append(" " + cause.message).appendLine()
          val newCause = cause.cause
          if (newCause !== cause) {
            cause = newCause ?: break
          }
        }
        assertThat(stringBuilder).isEqualToIgnoringWhitespace("""
            PluginException [com.intellij.serviceContainer.C1, com.intellij.serviceContainer.C2] [Plugin: test]
            CycleInitializationException [com.intellij.serviceContainer.C1, com.intellij.serviceContainer.C2]
          """.trimIndent())
      }
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