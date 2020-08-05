// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.configurationStore.StateStorageManager
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.messages.MessageBus

internal val testPluginDescriptor = DefaultPluginDescriptor("test")

internal class TestComponentManager(override var isGetComponentAdapterOfTypeCheckEnabled: Boolean = true) : ComponentManagerImpl(null, setExtensionsRootArea = false /* must work without */) {
  init {
    registerService(IComponentStore::class.java, TestComponentStore::class.java, testPluginDescriptor, false)
  }

  override fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl) = pluginDescriptor.appContainerDescriptor

  override fun getApplication(): Application? = null
}

private class TestComponentStore : IComponentStore {
  override val storageManager: StateStorageManager
    get() = TODO("not implemented")

  override fun setPath(path: String) {
  }

  override fun initComponent(component: Any, serviceDescriptor: ServiceDescriptor?, pluginId: PluginId?) {
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

  override fun removeComponent(name: String) {
  }
}