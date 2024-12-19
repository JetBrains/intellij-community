// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components.impl.stores

import com.intellij.configurationStore.StateStorageManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

@Internal
interface IComponentStore {
  val storageManager: StateStorageManager
  val isStoreInitialized: Boolean

  fun setPath(path: Path)

  fun initComponent(component: Any, serviceDescriptor: ServiceDescriptor?, pluginId: PluginId)

  fun unloadComponent(component: Any)

  fun initPersistencePlainComponent(component: Any, @NlsSafe key: String, pluginId: PluginId)

  fun reloadStates(componentNames: Set<String>)

  fun reloadState(componentClass: Class<out PersistentStateComponent<*>>)

  fun isReloadPossible(componentNames: Set<String>): Boolean

  suspend fun save(forceSavingAllSettings: Boolean = false)

  @TestOnly
  fun saveComponent(component: PersistentStateComponent<*>)

  @TestOnly
  fun removeComponent(name: String)

  @TestOnly
  fun clearCaches()

  fun release()
}

@Internal
interface ComponentStoreOwner {
  val componentStore: IComponentStore
}

@get:Internal
val ComponentManager.stateStore: IComponentStore
  get() = if (this is ComponentStoreOwner) this.componentStore else this.service<IComponentStore>()