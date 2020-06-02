// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Disposer
import com.intellij.workspaceModel.storage.CachedValueWithParameter
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import com.intellij.workspaceModel.storage.bridgeEntities.addLibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addLibraryPropertiesEntity
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectEntitiesLoader
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeModifiableBase
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

internal class ProjectModifiableLibraryTableBridgeImpl(
  originalStorage: WorkspaceEntityStorage,
  private val libraryTable: ProjectLibraryTableBridgeImpl,
  private val project: Project,
  diff: WorkspaceEntityStorageBuilder = WorkspaceEntityStorageBuilder.from(originalStorage)
) : LegacyBridgeModifiableBase(diff), LibraryTable.ModifiableModel {

  private val myLibrariesToAdd = mutableListOf<LibraryBridgeImpl>()
  private val myLibrariesToRemove = mutableListOf<Library>()

  private val librariesValue = CachedValueWithParameter { _: WorkspaceEntityStorage, (librariesToAdd, librariesToRemove): Pair<List<Library>, List<Library>> ->
    val libs = libraryTable.libraries.toMutableList()
    libs.removeAll(librariesToRemove)
    libs.addAll(librariesToAdd)
    return@CachedValueWithParameter libs.map { it.name to it }.toMap() to libs.toTypedArray()
  }

  private val libraries
    get() = WorkspaceModel.getInstance(project).entityStorage.cachedValue(librariesValue, myLibrariesToAdd to myLibrariesToRemove)

/*  private fun getLibraryModifiableModel(library: LibraryViaTypedEntity,
                                        diff: TypedEntityStorageBuilder): LegacyBridgeLibraryModifiableModelImpl {

    return LegacyBridgeLibraryModifiableModelImpl(
      originalLibrary = library,
      diff = diff,
      committer = { _, diffBuilder ->
        diff.addDiff(diffBuilder)
      })
  }*/

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

    return LibraryBridgeImpl(
      libraryTable = libraryTable,
      project = project,
      initialId = LibraryId(name, libraryTableId),
      initialEntityStorage = entityStorageOnDiff,
      parent = libraryTable,
      targetBuilder = this.diff
    ).also { libraryImpl ->
      myLibrariesToAdd.add(libraryImpl)
    }
  }

  override fun removeLibrary(library: Library) {
    assertModelIsLive()

    val currentStorage = entityStorageOnDiff.current

    val entityId = when (library) {
      is LibraryBridgeImpl -> library.entityId
      else -> error("Unknown libraryImpl class: ${library.javaClass.simpleName}")
    }

    val libraryEntity = currentStorage.resolve(entityId)
    if (libraryEntity != null) {
      diff.removeEntity(libraryEntity)
      if (myLibrariesToAdd.remove(library)) {
        Disposer.dispose(library)
      }
      else {
        myLibrariesToRemove.add(library)
      }
    }
  }

  override fun commit() {
    assertModelIsLive()
    modelIsCommittedOrDisposed = true

    myLibrariesToAdd.forEach { library ->
      library.clearTargetBuilder()
    }

    libraryTable.setNewLibraryInstances(myLibrariesToAdd)
    WorkspaceModel.getInstance(project).updateProjectModel {
      it.addDiff(diff)
    }
  }

  override fun getLibraryIterator(): Iterator<Library> = libraries.second.iterator()
  override fun getLibraryByName(name: String): Library? = libraries.first[name]
  override fun getLibraries(): Array<Library> = libraries.second

  override fun dispose() {
    modelIsCommittedOrDisposed = true

    myLibrariesToAdd.forEach { Disposer.dispose(it) }
    myLibrariesToAdd.clear()
  }

  override fun isChanged(): Boolean = !diff.isEmpty()
}
