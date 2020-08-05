// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl.stores

import com.intellij.configurationStore.SaveSession
import com.intellij.configurationStore.StateStorageManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.MessageBus
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.annotations.TestOnly

interface IComponentStore {
  val storageManager: StateStorageManager

  fun setPath(path: @SystemIndependent String)

  fun initComponent(component: Any, serviceDescriptor: ServiceDescriptor?, pluginId: PluginId?)

  @JvmDefault
  fun unloadComponent(component: Any) {
  }

  fun initPersistencePlainComponent(component: Any, key: String)

  fun reloadStates(componentNames: Set<String>, messageBus: MessageBus)

  fun reloadState(componentClass: Class<out PersistentStateComponent<*>>)

  fun isReloadPossible(componentNames: Set<String>): Boolean

  suspend fun save(forceSavingAllSettings: Boolean = false)

  @TestOnly
  fun saveComponent(component: PersistentStateComponent<*>)

  @TestOnly
  fun removeComponent(name: String)

  @JvmDefault
  fun release() {}
}

data class SaveSessionAndFile(val session: SaveSession, val file: VirtualFile)
