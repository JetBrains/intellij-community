// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.configurationStore.StateStorageManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.util.messages.MessageBus
import org.junit.Test

class ConstructorInjectionTest {
  private val pluginDescriptor = DefaultPluginDescriptor("test")

  @Test
  fun `interface extension`() {
    val componentManager = TestComponentManager()
    val area = componentManager.extensionArea
    val point = area.registerPoint("bar", Bar::class.java, pluginDescriptor)
    @Suppress("DEPRECATION")
    point.registerExtension(BarImpl())

    componentManager.instantiateClassWithConstructorInjection(Foo::class.java, Foo::class.java, pluginDescriptor.pluginId)
  }

  @Test
  fun `resolve light service`() {
    val componentManager = TestComponentManager()
    componentManager.registerService(IComponentStore::class.java, TestComponentStore::class.java, pluginDescriptor, false)
    componentManager.instantiateClassWithConstructorInjection(BarServiceClient::class.java, BarServiceClient::class.java.name, pluginDescriptor.pluginId)
  }
}

private interface Bar

private class BarImpl : Bar

private class Foo(@Suppress("UNUSED_PARAMETER") bar: BarImpl)

@Service
private class BarService

private class BarServiceClient(@Suppress("UNUSED_PARAMETER") bar: BarService)

private class TestComponentStore : IComponentStore {
  override val storageManager: StateStorageManager
    get() = TODO("not implemented")

  override fun setPath(path: String) {
  }

  override fun initComponent(component: Any, serviceDescriptor: ServiceDescriptor?) {
  }

  override fun initPersistencePlainComponent(component: Any, key: String) {
  }

  override fun reloadStates(componentNames: Set<String>, messageBus: MessageBus) {
  }

  override fun reloadState(componentClass: Class<out PersistentStateComponent<*>>) {
  }

  override fun isReloadPossible(componentNames: Set<String>) = false

  override suspend fun save(forceSavingAllSettings: Boolean) {
  }

  override fun saveComponent(component: PersistentStateComponent<*>) {
  }
}