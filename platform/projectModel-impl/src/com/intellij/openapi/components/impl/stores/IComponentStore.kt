// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components.impl.stores

import com.intellij.configurationStore.SaveSession
import com.intellij.configurationStore.StateStorageManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.MessageBus
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

@ApiStatus.Internal
interface IComponentStore {
  val storageManager: StateStorageManager

  fun setPath(path: Path)

  fun initComponent(component: Any, serviceDescriptor: ServiceDescriptor?, pluginId: PluginId?)

  fun unloadComponent(component: Any)

  fun initPersistencePlainComponent(component: Any, @NlsSafe key: String)

  fun reloadStates(componentNames: Set<String>, messageBus: MessageBus)

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

data class SaveSessionAndFile(val session: SaveSession, val file: VirtualFile)
