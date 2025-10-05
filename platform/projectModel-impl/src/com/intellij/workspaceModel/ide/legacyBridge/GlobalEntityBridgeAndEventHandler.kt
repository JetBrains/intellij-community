// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.roots.impl.libraries.CustomLibraryTableImpl
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GlobalEntityBridgeAndEventHandler {
  fun initializeBridgesAfterLoading(mutableStorage: MutableEntityStorage, initialEntityStorage: VersionedEntityStorage): () -> Unit
  fun initializeBridges(changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage)
  fun handleBeforeChangeEvents(event: VersionedStorageChange)
  fun handleChangedEvents(event: VersionedStorageChange)

  companion object {
    fun getAllGlobalEntityHandlers(eelMachine: EelMachine): List<GlobalEntityBridgeAndEventHandler> {
      val result = mutableListOf<GlobalEntityBridgeAndEventHandler>()
      result.add(GlobalLibraryTableBridge.getInstance(eelMachine))
      result.add(GlobalSdkTableBridge.getInstance(eelMachine))
      LibraryTablesRegistrar.getInstance().customLibraryTables.forEach { customLibraryTable ->
        customLibraryTable as CustomLibraryTableImpl
        result.add(customLibraryTable.getDelegate() as CustomLibraryTableBridge)
      }
      return result
    }
  }
}