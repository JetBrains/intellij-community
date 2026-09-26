// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Path

class LightServiceInstanceSupportTest {
  @Test
  fun `dynamic initializer is not returned for light service from unloading plugin classloader`() {
    val support = LightServiceInstanceSupport(TestComponentManager()) {
      error("Dynamic light service must not be registered")
    }

    val activeServiceClass = TestPluginAwareClassLoader(PluginAwareClassLoader.ACTIVE).loadTestLightServiceClass()
    assertNotNull(support.dynamicInstanceInitializer(activeServiceClass))

    val unloadingServiceClass = TestPluginAwareClassLoader(PluginAwareClassLoader.UNLOAD_IN_PROGRESS).loadTestLightServiceClass()
    assertNull(support.dynamicInstanceInitializer(unloadingServiceClass))
  }
}

@Service(Service.Level.PROJECT)
private class TestLightService

private class TestPluginAwareClassLoader(private val classLoaderState: Int) :
  ClassLoader(LightServiceInstanceSupportTest::class.java.classLoader), PluginAwareClassLoader {

  private val pluginId = PluginId.getId("test.light.service")
  private val descriptor = DefaultPluginDescriptor(pluginId, this)

  @Suppress("RAW_SCOPE_CREATION")
  private val coroutineScope = CoroutineScope(Job())

  fun loadTestLightServiceClass(): Class<*> {
    return loadClassInsideSelf(TestLightService::class.java.name) ?: error("Cannot load ${TestLightService::class.java.name}")
  }

  override fun getPluginDescriptor(): PluginDescriptor = descriptor
  override fun getPluginId(): PluginId = pluginId
  override fun getModuleId(): String? = null
  override fun getEdtTime(): Long = 0
  override fun getBackgroundTime(): Long = 0
  override fun getLoadedClassCount(): Long = 1
  override fun getFiles(): Collection<Path> = emptyList()
  override fun getState(): Int = classLoaderState
  override fun getPackagePrefix(): String? = null
  override fun getPluginCoroutineScope(): CoroutineScope = coroutineScope
  override fun tryLoadingClass(name: String, forceLoadFromSubPluginClassloader: Boolean): Class<*>? = loadClassInsideSelf(name)

  override fun loadClassInsideSelf(name: String): Class<*>? {
    findLoadedClass(name)?.let { return it }
    if (name != TestLightService::class.java.name) return null

    val resourceName = name.replace('.', '/') + ".class"
    val bytes = requireNotNull(parent.getResourceAsStream(resourceName)) { "Cannot find $resourceName" }.use { it.readBytes() }
    return defineClass(name, bytes, 0, bytes.size)
  }
}
