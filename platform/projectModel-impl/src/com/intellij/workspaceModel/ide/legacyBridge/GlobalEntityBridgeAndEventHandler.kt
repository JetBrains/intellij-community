// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.roots.impl.libraries.CustomLibraryTableImpl
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange

interface GlobalEntityBridgeAndEventHandler {
  fun initializeBridgesAfterLoading(mutableStorage: MutableEntityStorage, initialEntityStorage: VersionedEntityStorage): () -> Unit
  fun initializeBridges(changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage)
  fun handleBeforeChangeEvents(event: VersionedStorageChange)
  fun handleChangedEvents(event: VersionedStorageChange)

  companion object {
    fun getAllGlobalEntityHandlers(): List<GlobalEntityBridgeAndEventHandler> {
      val result = mutableListOf<GlobalEntityBridgeAndEventHandler>()
      result.add(GlobalLibraryTableBridge.getInstance())
      result.add(GlobalSdkTableBridge.getInstance())
      if (CustomLibraryTableBridge.isEnabled()) {
        LibraryTablesRegistrar.getInstance().customLibraryTables.forEach { customLibraryTable ->
          customLibraryTable as CustomLibraryTableImpl
          result.add(customLibraryTable.getDelegate() as CustomLibraryTableBridge)
        }
      }
      return result
    }
  }
}