// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LibraryEntityUtils")
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryNameGenerator.getLibraryTableId
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId

/**
 * @return [Library] or null if corresponding module is unloaded
 */
fun LibraryEntity.findLibraryBridge(snapshot: EntityStorage): Library? {
  return snapshot.libraryMap.getDataByEntity(this)
}

/**
 * @return [Library] calculated base on the [LibraryId] it can be application or project level lib
 */
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