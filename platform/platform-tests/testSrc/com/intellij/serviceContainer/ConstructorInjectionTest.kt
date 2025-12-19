// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax")

package com.intellij.serviceContainer

import com.intellij.ide.plugins.TestIdeaPluginDescriptor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.components.ServiceDescriptor.PreloadMode
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.fail

class ConstructorInjectionTest {
  @Test
  fun `light service getService() performance`() {
    val componentManager = TestComponentManager()
    assertThat(componentManager.getService(BarService::class.java)).isNotNull()

    Benchmark.newBenchmark("getService() must be fast for cached service") {
      repeat(30_000_000) {
        componentManager.getService(BarService::class.java)!!
      }
    }.runAsStressTest().start()
  }

  @Test
  fun `constructor with bool`() {
    val componentManager = TestComponentManager()
    componentManager.registerService(ConstructorWithBoolean::class.java, ConstructorWithBoolean::class.java, DefaultPluginDescriptor("test"), false)
    componentManager.getService(ConstructorWithBoolean::class.java)
  }

  @Test
  fun `test service factory method with bundled plugin`() {
    val componentManager = TestComponentManager()
    val serviceClassName = ConstructWithFactoryMethod::class.java.name
    componentManager.registerService(serviceDescriptor(serviceClassName, "${serviceClassName}#createServiceInstance"),
                                     IdeaPluginDescriptorForServiceRegistration(bundled = true))
    val service = componentManager.getService(ConstructWithFactoryMethod::class.java)
    assertNotNull(service)
    assertInstanceOf<ConstructWithFactoryMethod>(service)
  }

  @Test
  fun `test service factory method with non-bundled plugin`() {
    val componentManager = TestComponentManager()
    val serviceClassName = ConstructWithFactoryMethod::class.java.name
    componentManager.registerService(serviceDescriptor(serviceClassName, "${serviceClassName}#createServiceInstance"),
                                     IdeaPluginDescriptorForServiceRegistration(bundled = false))
    try {
      componentManager.getService(ConstructWithFactoryMethod::class.java)
      fail("Should throw ISE, because factory service methods in non-bundled plugins are forbidden")
    }
    catch (ise: IllegalStateException) {
      assertThat(ise).hasMessageStartingWith("Only bundled plugins may declare factory methods for classes.")
    }
  }

  private class IdeaPluginDescriptorForServiceRegistration(private val bundled: Boolean) : TestIdeaPluginDescriptor() {
    override fun getPluginId(): PluginId = PluginId("test")
    override fun getPluginClassLoader(): ClassLoader? = null
    override fun isBundled(): Boolean = bundled
  }

  private fun serviceDescriptor(serviceIfcName: String, serviceImplName: String): ServiceDescriptor {
    return ServiceDescriptor(serviceIfcName, serviceImplName,
                             null, null, false, null,
                             PreloadMode.FALSE, null, null)
  }
}
@Service
private class BarService
