// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.library

import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.storage.EntityStorage
import org.jetbrains.jps.model.ex.JpsElementBase
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides a way to quickly get all libraries from a library table without iterating over all library entities. 
 */
internal class JpsLibraryCollectionsCache(storage: EntityStorage) {
  private val libraryCollections = ConcurrentHashMap<LibraryTableId, JpsLibraryCollectionBridge>()
  private val libraryByTable = storage.entities(LibraryEntity::class.java).groupBy { it.tableId }
  private val sdkEntities = storage.entities(SdkEntity::class.java).toList()
  
  fun getLibraryCollection(tableId: LibraryTableId, parentElement: JpsElementBase<*>): JpsLibraryCollectionBridge {
    return libraryCollections.computeIfAbsent(tableId) {
      val libraryEntities = libraryByTable[tableId] ?: emptyList()
      JpsLibraryCollectionBridge(libraryEntities, sdkEntities.takeIf { tableId == GLOBAL_LIBRARY_TABLE_ID }, parentElement)
    }
  }
  
  companion object {
    val GLOBAL_LIBRARY_TABLE_ID = LibraryTableId.GlobalLibraryTableId("application")
  }
}
