// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax")

package com.intellij.serviceContainer

import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
}
@Service
private class BarService
