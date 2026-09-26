// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components.impl.stores

import com.intellij.configurationStore.StateStorageManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

@Internal
interface IComponentStore {
  val storageManager: StateStorageManager

  val isStoreInitialized: Boolean
    get() = true

  fun setPath(path: Path) {
  }

  suspend fun initComponent(component: Any, serviceDescriptor: ServiceDescriptor?, pluginId: PluginId, parentScope: CoroutineScope? = null)

  fun unloadComponent(component: Any)

  fun initPersistencePlainComponent(component: Any, @NlsSafe key: String, pluginId: PluginId)

  suspend fun reloadStates(componentNames: Set<String>)

  suspend fun reloadState(componentClass: Class<out PersistentStateComponent<*>>)

  fun isReloadPossible(componentNames: Set<String>): Boolean

  suspend fun save(forceSavingAllSettings: Boolean = false)

  @TestOnly
  fun saveComponent(component: PersistentStateComponent<*>)

  @TestOnly
  fun removeComponent(name: String)

  @TestOnly
  fun clearCaches() {
  }

  fun release() {
  }
}

@Internal
interface ComponentStoreOwner {
  val componentStore: IComponentStore
}

// for poor code in java
@Deprecated("Use coroutine version")
@Internal
fun scheduleReloadState(
  store: IComponentStore,
  componentClass: Class<out PersistentStateComponent<*>>,
  postAction: Runnable,
  coroutineScope: CoroutineScope,
) {
  coroutineScope.launch {
    store.reloadState(componentClass)
    postAction.run()
  }
}

@get:Internal
val ComponentManager.stateStore: IComponentStore
  get() = if (this is ComponentStoreOwner) this.componentStore else this.service<IComponentStore>()