// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import org.junit.Test

class ConstructorInjectionTest {
  private val pluginDescriptor = DefaultPluginDescriptor("test")

  @Test
  fun `interface extension`() {
    val componentManager = TestComponentManager(isGetComponentAdapterOfTypeCheckEnabled = false)
    val area = componentManager.extensionArea
    val point = area.registerPoint("bar", Bar::class.java, pluginDescriptor)
    @Suppress("DEPRECATION")
    point.registerExtension(BarImpl())

    componentManager.instantiateClassWithConstructorInjection(Foo::class.java, Foo::class.java, pluginDescriptor.pluginId)
  }

  @Test
  fun `resolve light service`() {
    val componentManager = TestComponentManager()
    componentManager.instantiateClassWithConstructorInjection(BarServiceClient::class.java, BarServiceClient::class.java.name, pluginDescriptor.pluginId)
  }

  @Test
  fun `constructor with bool`() {
    val componentManager = TestComponentManager()
    componentManager.registerService(ConstructorWithBoolean::class.java, ConstructorWithBoolean::class.java, testPluginDescriptor, false)
    componentManager.getService(ConstructorWithBoolean::class.java)
  }
}

private interface Bar

private class BarImpl : Bar

private class Foo(@Suppress("UNUSED_PARAMETER") bar: BarImpl)

@Service
private class BarService

private class BarServiceClient(@Suppress("UNUSED_PARAMETER") bar: BarService)