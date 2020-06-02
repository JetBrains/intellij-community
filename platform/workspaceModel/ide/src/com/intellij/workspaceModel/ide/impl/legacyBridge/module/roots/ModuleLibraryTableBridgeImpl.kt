// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.impl.ModuleLibraryTableBase
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Disposer
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridgeImpl

internal class ModuleLibraryTableBridgeImpl(private val moduleBridge: ModuleBridge) : ModuleLibraryTableBase(), ModuleLibraryTableBridge {
  internal val moduleLibraries = mutableListOf<LibraryBridgeImpl>()

  init {
    val moduleLibraryTableId = LibraryTableId.ModuleLibraryTableId(moduleBridge.moduleEntityId)

    moduleBridge.entityStorage.current
      .entities(LibraryEntity::class.java)
      .filter { it.tableId == moduleLibraryTableId }
      .forEach { libraryEntity ->
        addLibrary(libraryEntity.persistentId())
      }

    Disposer.register(moduleBridge, this)
  }

  override fun getLibraryIterator(): Iterator<Library> {
    return moduleLibraries.iterator()
  }

  override fun createLibrary(name: String?,
                             type: PersistentLibraryKind<*>?,
                             externalSource: ProjectModelExternalSource?): Library {
    error("Must not be called for read-only table")
  }

  override fun removeLibrary(library: Library) {
    error("Must not be called for read-only table")
  }

  override fun isChanged(): Boolean {
    return false
  }

  internal fun removeLibrary(libraryId: LibraryId) {
    val moduleLibrary = moduleLibraries.firstOrNull { it.entityId == libraryId }
                        ?: error("Could not find library '${libraryId.name}' in module ${module.name}")
    moduleLibraries.remove(moduleLibrary)
    Disposer.dispose(moduleLibrary)
  }

  internal fun updateLibrary(idBefore: LibraryId, idAfter: LibraryId) {
    val moduleLibrary = moduleLibraries.firstOrNull { it.entityId == idBefore }
                        ?: error("Could not find library '${idBefore.name}' in module ${module.name}")
    moduleLibrary.entityId = idAfter
  }

  internal fun addLibrary(libraryId: LibraryId): LibraryBridgeImpl {
    val library = LibraryBridgeImpl(
      libraryTable = this,
      project = module.project,
      initialId = libraryId,
      initialEntityStorage = moduleBridge.entityStorage,
      parent = this,
      targetBuilder = null
    )
    moduleLibraries.add(library)
    return library
  }

  override val module: Module
    get() = moduleBridge
}