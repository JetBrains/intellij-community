// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.impl.ModuleLibraryTableBase
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Disposer
import com.intellij.workspace.api.LibraryEntity
import com.intellij.workspace.api.LibraryId
import com.intellij.workspace.api.LibraryTableId
import com.intellij.workspace.ide.WorkspaceModel
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibraryImpl

internal class LegacyBridgeModuleLibraryTableImpl(private val legacyBridgeModule: LegacyBridgeModule) : ModuleLibraryTableBase(), LegacyBridgeModuleLibraryTable {
  internal val moduleLibraries = mutableListOf<LegacyBridgeLibraryImpl>()

  init {
    val moduleLibraryTableId = LibraryTableId.ModuleLibraryTableId(legacyBridgeModule.moduleEntityId)

    legacyBridgeModule.entityStore.current
      .entities(LibraryEntity::class.java)
      .filter { it.tableId == moduleLibraryTableId }
      .forEach { libraryEntity ->
        addLibrary(libraryEntity.persistentId())
      }

    Disposer.register(legacyBridgeModule, this)
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

  internal fun addLibrary(libraryId: LibraryId): LegacyBridgeLibraryImpl {
    val library = LegacyBridgeLibraryImpl(
      libraryTable = this,
      project = module.project,
      initialId = libraryId,
      initialEntityStore = legacyBridgeModule.entityStore,
      parent = this,
      targetBuilder = null
    )
    moduleLibraries.add(library)
    return library
  }

  override val module: Module
    get() = legacyBridgeModule
}