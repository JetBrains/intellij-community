// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryPropertiesEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.storage.CachedValue
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.toSnapshot
import com.intellij.workspaceModel.ide.impl.LegacyBridgeJpsEntitySourceFactory
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeModifiableBase
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.findLibraryEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap
import com.intellij.workspaceModel.ide.legacyBridge.ProjectModifiableLibraryTableBridge
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

internal class ProjectModifiableLibraryTableBridgeImpl(
  originalStorage: EntityStorage,
  private val libraryTable: ProjectLibraryTableBridgeImpl,
  private val project: Project,
  diff: MutableEntityStorage = MutableEntityStorage.from(originalStorage.toSnapshot()),
  cacheStorageResult: Boolean = true
) : LegacyBridgeModifiableBase(diff, cacheStorageResult), ProjectModifiableLibraryTableBridge {

  private val myAddedLibraries = mutableListOf<LibraryBridgeImpl>()

  private val librariesArrayValue = CachedValue<Array<Library>> { storage ->
    storage.entities(LibraryEntity::class.java).filter { it.tableId == LibraryTableId.ProjectLibraryTableId }
      .mapNotNull { entity -> storage.libraryMap.getDataByEntity(entity) }
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

    val libraryId = LibraryId(name, libraryTableId)
    if (libraryId in diff) {
      // We log the error, but don't break the execution as technically project model can handle this case.
      // The existing library entity will be replaced with the new created one.
      LOG.error("Project library with name '$name' already exists.")
    }

    val libraryEntity = diff addEntity LibraryEntity(name, libraryTableId, emptyList(), LegacyBridgeJpsEntitySourceFactory.createEntitySourceForProjectLibrary(project, externalSource))

    if (type != null) {
      diff addEntity LibraryPropertiesEntity(libraryType = type.kindId,
                                             entitySource = libraryEntity.entitySource) {
        library = libraryEntity
        propertiesXmlTag = serializeComponentAsString(JpsLibraryTableSerializer.PROPERTIES_TAG, type.createDefaultProperties())
      }
    }

    val library = LibraryBridgeImpl(
      libraryTable = libraryTable,
      project = project,
      initialId = libraryId,
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
    prepareForCommit()
    WorkspaceModel.getInstance(project).updateProjectModel("Project library table commit") {
      it.applyChangesFrom(diff)
    }
    librariesArray.forEach { library -> (library as LibraryBridgeImpl).clearTargetBuilder() }
  }

  override fun prepareForCommit() {
    assertModelIsLive()
    modelIsCommittedOrDisposed = true
    val storage = WorkspaceModel.getInstance(project).currentSnapshot
    myAddedLibraries.forEach { library ->
      if (library.libraryId in storage) {
        // it may happen that actual library table already has a library with such name (e.g. when multiple projects are imported in parallel)
        // in such case we need to skip the new library to avoid exceptions.
        diff.removeEntity(diff.libraryMap.getEntities(library).first())
        Disposer.dispose(library)
      }
      library.clearTargetBuilder()
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
    librariesArray.forEach { library -> (library as LibraryBridgeImpl).clearTargetBuilder() }
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun isChanged(): Boolean = (diff as MutableEntityStorageInstrumentation).hasChanges()

  companion object {
    val LOG = logger<ProjectModifiableLibraryTableBridgeImpl>()
  }
}
