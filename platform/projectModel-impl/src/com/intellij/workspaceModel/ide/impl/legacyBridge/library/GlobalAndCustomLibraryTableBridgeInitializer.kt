// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.backend.workspace.BridgeInitializer
import com.intellij.platform.eel.provider.getEelMachine
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap

internal class GlobalAndCustomLibraryTableBridgeInitializer : BridgeInitializer {
  override fun isEnabled(): Boolean = true

  // Handle the initialization of all global and custom libraries
  override fun initializeBridges(project: Project,
                                 changes: Map<Class<*>, List<EntityChange<*>>>,
                                 builder: MutableEntityStorage) = GlobalLibraryTableBridgeImpl.initializeLibraryBridgesTimeMs.addMeasuredTime {
    val machine = project.getEelMachine()
    val entityStorage = GlobalWorkspaceModel.getInstance(machine).entityStorage

    @Suppress("UNCHECKED_CAST")
    val libraryChanges = (changes[LibraryEntity::class.java] as? List<EntityChange<LibraryEntity>>) ?: emptyList()
    val addChanges = libraryChanges.filterGlobalOrCustomLibraryChanges().filterIsInstance<EntityChange.Added<LibraryEntity>>()

    for (addChange in addChanges) {
      val libraryEntity = addChange.newEntity
      // In most cases the bridge comes from the global storage together with the entity
      if (builder.libraryMap.getDataByEntity(libraryEntity) != null) continue

      val level = libraryEntity.tableId.level
      val libraryTable = getGlobalOrCustomLibraryTable(level)
      if (libraryTable == null) {
        LOG.warn("Skip the bridge for the library '${libraryEntity.name}': the library table '$level' is not registered")
        continue
      }
      builder.mutableLibraryMap.addIfAbsent(libraryEntity, LibraryBridgeImpl(
        libraryTable = libraryTable,
        origin = LibraryOrigin.OfMachine(machine),
        initialId = libraryEntity.symbolicId,
        initialEntityStorage = entityStorage,
        targetBuilder = builder,
      ))
    }
  }

  private fun getGlobalOrCustomLibraryTable(level: String): LibraryTable? {
    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    return when (level) {
      LibraryTablesRegistrar.APPLICATION_LEVEL -> libraryTablesRegistrar.libraryTable
      else -> libraryTablesRegistrar.getCustomLibraryTableByLevel(level)
    }
  }

  companion object {
    private val LOG = logger<GlobalAndCustomLibraryTableBridgeInitializer>()
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