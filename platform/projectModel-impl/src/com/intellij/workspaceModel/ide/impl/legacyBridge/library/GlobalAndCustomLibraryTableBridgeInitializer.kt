// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.backend.workspace.BridgeInitializer
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap

internal class GlobalAndCustomLibraryTableBridgeInitializer : BridgeInitializer {
  override fun isEnabled(): Boolean = true

  // Handle the initialization of all global and custom libraries
  override fun initializeBridges(project: Project,
                                 changes: Map<Class<*>, List<EntityChange<*>>>,
                                 builder: MutableEntityStorage) = GlobalLibraryTableBridgeImpl.initializeLibraryBridgesTimeMs.addMeasuredTime {
    val machine = project.getEelDescriptor().machine
    val entityStorage = GlobalWorkspaceModel.getInstance(machine).entityStorage

    @Suppress("UNCHECKED_CAST")
    val libraryChanges = (changes[LibraryEntity::class.java] as? List<EntityChange<LibraryEntity>>) ?: emptyList()
    val addChanges = libraryChanges.filterGlobalOrCustomLibraryChanges().filterIsInstance<EntityChange.Added<LibraryEntity>>()

    for (addChange in addChanges) {
      // Will initialize the bridge if missing
      builder.mutableLibraryMap.getOrPutDataByEntity(addChange.newEntity) {
        LibraryBridgeImpl(
          libraryTable = getGlobalOrCustomLibraryTable(addChange.newEntity.symbolicId.tableId.level),
          origin = LibraryOrigin.OfMachine(machine),
          initialId = addChange.newEntity.symbolicId,
          initialEntityStorage = entityStorage,
          targetBuilder = builder
        )
      }
    }
  }

  private fun getGlobalOrCustomLibraryTable(tableId: String): LibraryTable {
    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    return when (tableId) {
      LibraryTablesRegistrar.APPLICATION_LEVEL -> libraryTablesRegistrar.libraryTable
      else -> libraryTablesRegistrar.getCustomLibraryTableByLevel(tableId)!!
    }
  }
}

private fun List<EntityChange<LibraryEntity>>.filterGlobalOrCustomLibraryChanges(): List<EntityChange<LibraryEntity>> {
  return filter {
    when (it) {
      is EntityChange.Added -> it.newEntity.tableId is LibraryTableId.GlobalLibraryTableId
      is EntityChange.Removed -> it.oldEntity.tableId is LibraryTableId.GlobalLibraryTableId
      is EntityChange.Replaced -> it.oldEntity.tableId is LibraryTableId.GlobalLibraryTableId
    }
  }
}