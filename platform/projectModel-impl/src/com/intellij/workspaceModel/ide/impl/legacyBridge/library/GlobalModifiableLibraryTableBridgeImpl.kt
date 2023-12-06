// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Disposer
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryPropertiesEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.LegacyBridgeJpsEntitySourceFactory
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeModifiableBase
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.findLibraryEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

internal class GlobalModifiableLibraryTableBridgeImpl(private val libraryTable: LibraryTable):
  LegacyBridgeModifiableBase(MutableEntityStorage.from(GlobalWorkspaceModel.getInstance().currentSnapshot), true),
  LibraryTable.ModifiableModel {
  private val myAddedLibraries = mutableListOf<LibraryBridgeImpl>()

  override fun createLibrary(name: String?): Library {
    return createLibrary(name = name, type = null)
  }

  override fun createLibrary(name: String?, type: PersistentLibraryKind<*>?): Library {
    return createLibrary(name = name, type = type, externalSource = null)
  }

  override fun createLibrary(name: String?, type: PersistentLibraryKind<*>?, externalSource: ProjectModelExternalSource?): Library {
    if (name.isNullOrBlank()) error("Application Library must have a name")
    assertModelIsLive()

    val libraryTableId = LibraryTableId.GlobalLibraryTableId(LibraryTablesRegistrar.APPLICATION_LEVEL)

    val libraryEntity = diff addEntity LibraryEntity(name, libraryTableId, emptyList(),
                                                     LegacyBridgeJpsEntitySourceFactory.createEntitySourceForGlobalLibrary())

    if (type != null) {
      diff addEntity LibraryPropertiesEntity(libraryType = type.kindId,
                                             entitySource = libraryEntity.entitySource) {
        library = libraryEntity
        propertiesXmlTag = serializeComponentAsString(JpsLibraryTableSerializer.PROPERTIES_TAG,
                                                      type.createDefaultProperties())
      }
    }

    val library = LibraryBridgeImpl(
      libraryTable = libraryTable,
      project = null,
      initialId = LibraryId(name, libraryTableId),
      initialEntityStorage = entityStorageOnDiff,
      targetBuilder = this.diff
    )
    myAddedLibraries.add(library)
    diff.mutableLibraryMap.addMapping(libraryEntity, library)
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
    GlobalWorkspaceModel.getInstance().updateModel("Global library table commit") {
      it.addDiff(diff)
    }
    libraries.forEach { library -> (library as LibraryBridgeImpl).clearTargetBuilder() }
  }

  override fun getLibraryIterator(): Iterator<Library> = libraries.iterator()

  override fun getLibraryByName(name: String): Library? {
    val libraryEntity = diff.resolve(LibraryId(name, LibraryTableId.GlobalLibraryTableId(LibraryTablesRegistrar.APPLICATION_LEVEL))) ?: return null
    return diff.libraryMap.getDataByEntity(libraryEntity)
  }

  override fun getLibraries(): Array<Library> {
    return diff.entities(LibraryEntity::class.java).filter { it.tableId::class == LibraryTableId.GlobalLibraryTableId::class }
      .mapNotNull { diff.libraryMap.getDataByEntity(it) }
      .toList().toTypedArray()
  }

  override fun isChanged(): Boolean = diff.hasChanges()


  override fun dispose() {
    modelIsCommittedOrDisposed = true

    myAddedLibraries.forEach { Disposer.dispose(it) }
    myAddedLibraries.clear()
    libraries.forEach { library -> (library as LibraryBridgeImpl).clearTargetBuilder() }
  }
}