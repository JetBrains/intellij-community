// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax")

package com.intellij.serviceContainer

import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConstructorInjectionTest {
  private val pluginDescriptor = DefaultPluginDescriptor("test")

  @Test
  fun `light service getService() performance`() {
    val componentManager = TestComponentManager()
    assertThat(componentManager.getService(BarService::class.java)).isNotNull()
    Benchmark.newBenchmark("getService() must be fast for cached service") {
      for (i in 0..30_000_000) {
        componentManager.getService(BarService::class.java)!!
      }
    }.start()
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

//private class BarServiceClient(@Suppress("UNUSED_PARAMETER") bar: BarService)