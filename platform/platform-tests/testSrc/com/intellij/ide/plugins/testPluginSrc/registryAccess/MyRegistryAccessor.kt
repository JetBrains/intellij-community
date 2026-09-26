// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.testPluginSrc.registryAccess

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.testFramework.plugins.PluginTestHandle
import kotlinx.atomicfu.atomic

internal class MyRegistryAccessor : Runnable {
  override fun run() {
    service<MyRegistryAccessorService>().accessRegistry()
  }
}

@Service
internal class MyRegistryAccessorService : PluginTestHandle<Unit, Int> {
  private var accessCount = atomic(0)

  override fun test(arg: Unit): Int = accessCount.value

  fun accessRegistry() {
    @Suppress("UnresolvedPluginConfigReference")
    check(Registry.get("test.plugin.registry.key").asBoolean())
    accessCount.incrementAndGet()
  }
}
