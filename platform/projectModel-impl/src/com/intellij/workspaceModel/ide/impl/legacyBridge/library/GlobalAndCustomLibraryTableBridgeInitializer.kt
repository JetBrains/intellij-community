// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.backend.workspace.BridgeInitializer
import com.intellij.platform.diagnostic.telemetry.helpers.addMeasuredTimeMillis
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap

class GlobalAndCustomLibraryTableBridgeInitializer : BridgeInitializer {
  override fun isEnabled(): Boolean = true

  // Handle the initialization of all global and custom libraries
  override fun initializeBridges(project: Project,
                                 changes: Map<Class<*>, List<EntityChange<*>>>,
                                 builder: MutableEntityStorage) = GlobalLibraryTableBridgeImpl.initializeLibraryBridgesTimeMs.addMeasuredTimeMillis {
    val entityStorage = GlobalWorkspaceModel.getInstance().entityStorage

    @Suppress("UNCHECKED_CAST")
    val libraryChanges = (changes[LibraryEntity::class.java] as? List<EntityChange<LibraryEntity>>) ?: emptyList()
    val addChanges = libraryChanges.filterGlobalOrCustomLibraryChanges().filterIsInstance<EntityChange.Added<LibraryEntity>>()

    for (addChange in addChanges) {
      // Will initialize the bridge if missing
      builder.mutableLibraryMap.getOrPutDataByEntity(addChange.entity) {
        LibraryBridgeImpl(
          libraryTable = getGlobalOrCustomLibraryTable(addChange.entity.symbolicId.tableId.level),
          project = null,
          initialId = addChange.entity.symbolicId,
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
      is EntityChange.Added -> it.entity.tableId is LibraryTableId.GlobalLibraryTableId
      is EntityChange.Removed -> it.entity.tableId is LibraryTableId.GlobalLibraryTableId
      is EntityChange.Replaced -> it.oldEntity.tableId is LibraryTableId.GlobalLibraryTableId
    }
  }
}