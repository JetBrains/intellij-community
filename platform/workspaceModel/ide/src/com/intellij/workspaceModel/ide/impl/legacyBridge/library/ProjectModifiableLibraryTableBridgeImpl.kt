// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Disposer
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectEntitiesLoader
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeModifiableBase
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.findLibraryEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap
import com.intellij.workspaceModel.storage.CachedValue
import com.intellij.workspaceModel.storage.bridgeEntities.*
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

internal class ProjectModifiableLibraryTableBridgeImpl(
  originalStorage: WorkspaceEntityStorage,
  private val libraryTable: ProjectLibraryTableBridgeImpl,
  private val project: Project,
  diff: WorkspaceEntityStorageBuilder = WorkspaceEntityStorageBuilder.from(originalStorage)
) : LegacyBridgeModifiableBase(diff), LibraryTable.ModifiableModel {

  private val myAddedLibraries = mutableListOf<LibraryBridgeImpl>()

  private val librariesArrayValue = CachedValue<Array<Library>> { storage ->
    storage.entities(LibraryEntity::class.java).filter { it.tableId == LibraryTableId.ProjectLibraryTableId }
      .mapNotNull { storage.libraryMap.getDataByEntity(it) }
      .toList().toTypedArray()
  }

  private val librariesArray: Array<Library>
    get() = entityStorageOnDiff.cachedValue(librariesArrayValue)

  override fun createLibrary(name: String?): Library = createLibrary(name = name, type = null)

  override fun createLibrary(name: String?, type: PersistentLibraryKind<out LibraryProperties<*>>?): Library =
    createLibrary(name = name, type = type, externalSource = null)

  override fun createLibrary(name: String?,
                             type: PersistentLibraryKind<out LibraryProperties<*>>?,
                             externalSource: ProjectModelExternalSource?): Library {

    if (name.isNullOrBlank()) error("Project Library must have a name")

    assertModelIsLive()

    val libraryTableId = LibraryTableId.ProjectLibraryTableId

    val libraryEntity = diff.addLibraryEntity(
      roots = emptyList(),
      tableId = LibraryTableId.ProjectLibraryTableId,
      name = name,
      excludedRoots = emptyList(),
      source = JpsProjectEntitiesLoader.createEntitySourceForProjectLibrary(project, externalSource)
    )

    if (type != null) {
      diff.addLibraryPropertiesEntity(
        library = libraryEntity,
        libraryType = type.kindId,
        propertiesXmlTag = serializeComponentAsString(JpsLibraryTableSerializer.PROPERTIES_TAG, type.createDefaultProperties()),
        source = libraryEntity.entitySource
      )
    }

    val library = LibraryBridgeImpl(
      libraryTable = libraryTable,
      project = project,
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
      diff.removeEntity(libraryEntity)
      if (myAddedLibraries.remove(library)) {
        Disposer.dispose(library)
      }
    }
  }

  override fun commit() {
    assertModelIsLive()
    modelIsCommittedOrDisposed = true

    myAddedLibraries.forEach { library ->
      library.clearTargetBuilder()
    }

    WorkspaceModel.getInstance(project).updateProjectModel {
      it.addDiff(diff)
    }
  }

  override fun getLibraryIterator(): Iterator<Library> = librariesArray.iterator()

  override fun getLibraryByName(name: String): Library? {
    val libraryEntity = diff.resolve(LibraryId(name, LibraryTableId.ProjectLibraryTableId)) ?: return null
    return diff.libraryMap.getDataByEntity(libraryEntity)
  }
  override fun getLibraries(): Array<Library> = librariesArray

  override fun dispose() {
    modelIsCommittedOrDisposed = true

    myAddedLibraries.forEach { Disposer.dispose(it) }
    myAddedLibraries.clear()
  }

  override fun isChanged(): Boolean = !diff.isEmpty()
}
