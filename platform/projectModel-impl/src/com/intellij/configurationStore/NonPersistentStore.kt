// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
object NonPersistentStore : IComponentStore {
  override val storageManager: StateStorageManager
    get() = NonPersistentStateStorageManager

  override suspend fun initComponent(
    component: Any,
    serviceDescriptor: ServiceDescriptor?,
    pluginId: PluginId,
    parentScope: CoroutineScope?,
  ) {
  }

  override fun unloadComponent(component: Any) {}

  override fun initPersistencePlainComponent(component: Any, key: String, pluginId: PluginId) {
  }

  override suspend fun reloadStates(componentNames: Set<String>) {}

  override suspend fun reloadState(componentClass: Class<out PersistentStateComponent<*>>) {
  }

  override fun isReloadPossible(componentNames: Set<String>): Boolean = true

  override suspend fun save(forceSavingAllSettings: Boolean) {}

  override fun saveComponent(component: PersistentStateComponent<*>) {
  }

  override fun removeComponent(name: String) {
  }
}

private object NonPersistentStateStorageManager : StateStorageManager {
  override val componentManager: ComponentManager?
    get() = null

  override fun getStateStorage(storageSpec: Storage): StateStorage = NonPersistentStateStorage

  override fun addStreamProvider(provider: StreamProvider, first: Boolean) {}

  override fun removeStreamProvider(aClass: Class<out StreamProvider>) {}

  override fun getOldStorage(component: Any, componentName: String, operation: StateStorageOperation): StateStorage? = null

  override fun expandMacro(collapsedPath: String): Path = Path.of(collapsedPath)

  override fun collapseMacro(path: String): String = path

  override val streamProvider: StreamProvider
    get() = DummyStreamProvider
}

private object NonPersistentStateStorage : StateStorage {
  override fun <T : Any> getState(
    component: Any?,
    componentName: String,
    pluginId: PluginId,
    stateClass: Class<T>,
    mergeInto: T?,
    reload: Boolean,
  ): T? = null

  override fun createSaveSessionProducer(): SaveSessionProducer? = null

  override suspend fun analyzeExternalChangesAndUpdateIfNeeded(componentNames: MutableSet<in String>) {}
}