// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.util.ref.GCUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference
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

  /**
   * IJPL-258458 Both containers of the component manager must take the predicate that stops the cache
   * from keeping a missing holder for a key class of an unloading plugin.
   */
  @Test
  fun `a lookup of a key class of an unloading plugin with no holder does not keep the class loader`() {
    val componentManager = TestComponentManager()
    try {
      val classLoaderRef = lookUpUnloadingServiceClass(componentManager)
      GCUtil.tryGcSoftlyReachableObjects { classLoaderRef.get() == null }
      assertNull(classLoaderRef.get(), "The component manager keeps the class loader of an unloading plugin")
    }
    finally {
      Disposer.dispose(componentManager.serviceParentDisposable)
    }
  }
}

/**
 * Runs the lookups in a separate frame, so that no local keeps the class loader alive during the GC.
 */
private fun lookUpUnloadingServiceClass(componentManager: TestComponentManager): WeakReference<ClassLoader> {
  val classLoader = TestPluginAwareClassLoader(PluginAwareClassLoader.UNLOAD_IN_PROGRESS)
  val serviceClass = classLoader.loadTestLightServiceClass()
  // the service container, both the plain and the dynamic lookup
  assertNull(componentManager.getServiceIfCreated(serviceClass))
  assertNull(componentManager.getService(serviceClass))
  // the component container, then the service container again
  assertFalse(componentManager.hasComponent(serviceClass))
  return WeakReference(classLoader)
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
