// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.configurationStore.StateStorageManager
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import kotlin.coroutines.EmptyCoroutineContext

val testPluginDescriptor: DefaultPluginDescriptor = DefaultPluginDescriptor("test")

@OptIn(DelicateCoroutinesApi::class)
@TestOnly
class TestComponentManager(override var isGetComponentAdapterOfTypeCheckEnabled: Boolean = true, val parentScope: CoroutineScope = GlobalScope) :
  ComponentManagerImpl(
    parent = null,
    parentScope = parentScope,
    additionalContext = EmptyCoroutineContext,
  ) {
  init {
    registerService(
      serviceInterface = IComponentStore::class.java,
      implementation = TestComponentStore::class.java,
      pluginDescriptor = testPluginDescriptor,
      override = false,
    )
  }

  override fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl) = pluginDescriptor.appContainerDescriptor

  override fun getApplication(): Application? = null
}

private class TestComponentStore : IComponentStore {
  override val storageManager: StateStorageManager
    get() = TODO("not implemented")
  override val isStoreInitialized: Boolean = true

  override fun setPath(path: Path) {
  }

  override fun initComponent(component: Any, serviceDescriptor: ServiceDescriptor?, pluginId: PluginId) {
  }

  override fun unloadComponent(component: Any) {
  }

  override fun initPersistencePlainComponent(component: Any, key: String, pluginId: PluginId) {
  }

  override fun reloadStates(componentNames: Set<String>) {
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

  override fun clearCaches() {
  }

  override fun release() {
  }
}