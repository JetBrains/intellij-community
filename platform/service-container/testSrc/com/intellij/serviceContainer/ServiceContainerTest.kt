// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import org.junit.Test

class ServiceContainerTest {
  @Test
  fun `cyclic detection`() {
    val componentManager = TestComponentManager()
    val pluginDescriptor = DefaultPluginDescriptor("test")
    componentManager.registerServiceInstance(ComponentManager::class.java, componentManager, pluginDescriptor)

    componentManager.registerService(C1::class.java, C1::class.java, pluginDescriptor, override = false)
    componentManager.registerService(C2::class.java, C2::class.java, pluginDescriptor, override = false)

    componentManager.getService(C1::class.java)
  }
}

internal class TestComponentManager : PlatformComponentManagerImpl(null, setExtensionsRootArea = false /* must work without */) {
  override fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl) = pluginDescriptor.appContainerDescriptor

  override fun assertExtensionInjection(pluginId: PluginId?, e: Exception) {
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