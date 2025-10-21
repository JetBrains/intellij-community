// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeModifiableBase
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.findLibraryEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

internal class GlobalOrCustomModifiableLibraryTableBridgeImpl(private val libraryTable: LibraryTable, val machine: EelMachine, private val entitySource: EntitySource) :
  LegacyBridgeModifiableBase(MutableEntityStorage.from(GlobalWorkspaceModel.getInstance(machine).currentSnapshot), true),
  LibraryTable.ModifiableModel {

  private val myAddedLibraries = mutableListOf<LibraryBridgeImpl>()
  private val libraryTableId = LibraryTableId.GlobalLibraryTableId(libraryTable.tableLevel)

  override fun createLibrary(name: String?): Library {
    return createLibrary(name = name, type = null)
  }

  override fun createLibrary(name: String?, type: PersistentLibraryKind<*>?): Library {
    return createLibrary(name = name, type = type, externalSource = null)
  }

  override fun createLibrary(name: String?, type: PersistentLibraryKind<*>?, externalSource: ProjectModelExternalSource?): Library {
    if (name.isNullOrBlank()) error("${libraryTableId.level} library must have a name")
    assertModelIsLive()

    val libraryEntity = LibraryEntity(name, libraryTableId, emptyList(), entitySource) {
      this.typeId = type?.kindId?.let { LibraryTypeId(it) }
    }

    val addedLibrary = if (type != null) {
      libraryEntity.libraryProperties = LibraryPropertiesEntity(libraryEntity.entitySource) {
        propertiesXmlTag = serializeComponentAsString(JpsLibraryTableSerializer.PROPERTIES_TAG, type.createDefaultProperties())
      }
      diff addEntity libraryEntity
    }
    else {
      diff addEntity libraryEntity
    }

    val library = LibraryBridgeImpl(
      libraryTable = libraryTable,
      origin = LibraryOrigin.OfMachine(machine),
      initialId = LibraryId(name, libraryTableId),
      initialEntityStorage = entityStorageOnDiff,
      targetBuilder = this.diff
    )
    myAddedLibraries.add(library)
    diff.mutableLibraryMap.addMapping(addedLibrary, library)
    return library
  }

  override fun removeLibrary(library: Library) {
    assertModelIsLive()

    val libraryEntity = entityStorageOnDiff.current.findLibraryEntity(library as LibraryBridge)
    if (libraryEntity != null) {
      (library as LibraryBridgeImpl).clearTargetBuilder()
      diff.removeEntity(libraryEntity)
      if (myAddedLibraries.remove(library)) {
        Disposer.dispose(library)
      }
    }
  }

  override fun commit() {
    GlobalWorkspaceModel.getInstance(machine).updateModel("${libraryTableId.level} library table commit") {
      it.applyChangesFrom(diff)
    }
    libraries.forEach { library -> (library as LibraryBridgeImpl).clearTargetBuilder() }
  }

  override fun getLibraryIterator(): Iterator<Library> = libraries.iterator()

  override fun getLibraryByName(name: String): Library? {
    val libraryEntity = diff.resolve(LibraryId(name, libraryTableId)) ?: return null
    return diff.libraryMap.getDataByEntity(libraryEntity)
  }

  override fun getLibraries(): Array<Library> {
    return diff.entities(LibraryEntity::class.java).filter { it.tableId == libraryTableId }
      .mapNotNull { diff.libraryMap.getDataByEntity(it) }
      .toList().toTypedArray()
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun isChanged(): Boolean = (diff as MutableEntityStorageInstrumentation).hasChanges()

  override fun dispose() {
    modelIsCommittedOrDisposed = true

    myAddedLibraries.forEach { Disposer.dispose(it) }
    myAddedLibraries.clear()
    libraries.forEach { library -> (library as LibraryBridgeImpl).clearTargetBuilder() }
  }
}