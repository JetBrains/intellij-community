// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.serialization.impl.LibraryNameGenerator.getLibraryTableId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Obsolete


/**
 * @return corresponding [LibraryEntity] or null if library isn't associated with entity yet
 */
fun Library.findLibraryEntity(entityStorage: EntityStorage): LibraryEntity? {
  return entityStorage.libraryMap.getEntities(this as LibraryBridge).firstOrNull() as LibraryEntity?
}

/**
 * Consider rewriting your code to use [LibraryEntity] directly. This method was introduced to simplify the first
 * step of migration to [WorkspaceModel][com.intellij.platform.backend.workspace.WorkspaceModel] and lately will
 * be removed.
 *
 * @return [Library] or null if corresponding module is unloaded
 */
@Obsolete
@ApiStatus.Internal
fun LibraryEntity.findLibraryBridge(snapshot: EntityStorage): Library? {
  return snapshot.libraryMap.getDataByEntity(this)
}

/**
 * Consider rewriting your code to use [LibraryEntity] directly. This method was introduced to simplify the first
 * step of migration to [WorkspaceModel][com.intellij.platform.backend.workspace.WorkspaceModel] and lately will
 * be removed.
 *
 * @return [Library] calculated base on the [LibraryId] it can be application or project level lib
 */
@Obsolete
@ApiStatus.Internal
fun LibraryId.findLibraryBridge(snapshot: EntityStorage, project: Project): Library? {
  return if (tableId is LibraryTableId.GlobalLibraryTableId) {
    LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(tableId.level, project)?.getLibraryByName(name)
  }
  else snapshot.resolve(this)?.findLibraryBridge(snapshot)
}

fun findLibraryId(library: Library): LibraryId {
  return when (library) {
    is LibraryBridge -> library.libraryId
    else -> LibraryId(library.name!!, getLibraryTableId(library.table.tableLevel))
  }
}