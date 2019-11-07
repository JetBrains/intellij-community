package com.intellij.workspace.legacyBridge.libraries.libraries

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Disposer
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.ExternalEntitySource
import com.intellij.workspace.ide.IdeUiEntitySource
import com.intellij.workspace.ide.WorkspaceModel
import com.intellij.workspace.legacyBridge.typedModel.library.LibraryViaTypedEntity

class LegacyBridgeProjectModifiableLibraryTableImpl(
  originalStorage: TypedEntityStorage,
  private val libraryTable: LegacyBridgeProjectLibraryTableImpl,
  private val project: Project
) : LegacyBridgeModifiableBase(originalStorage), LibraryTable.ModifiableModel {

  private val myLibrariesToAdd = mutableListOf<LegacyBridgeLibraryImpl>()
  private val myLibrariesToRemove = mutableListOf<Library>()

  private val librariesValue = CachedValueWithParameter { _: TypedEntityStorage, (librariesToAdd, librariesToRemove): Pair<List<Library>, List<Library>> ->
    val libs = libraryTable.libraries.toMutableList()
    libs.removeAll(librariesToRemove)
    libs.addAll(librariesToAdd)
    return@CachedValueWithParameter libs.map { it.name to it }.toMap() to libs.toTypedArray()
  }

  private val libraries
    get() = WorkspaceModel.getInstance(project).entityStore.cachedValue(librariesValue, myLibrariesToAdd to myLibrariesToRemove)

  private fun getLibraryModifiableModel(library: LibraryViaTypedEntity): LegacyBridgeLibraryModifiableModelImpl {
    return LegacyBridgeLibraryModifiableModelImpl(
      originalLibrary = library,
      committer = { _, diffBuilder ->
        diff.addDiff(diffBuilder)
      })
  }

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
      source = if (externalSource != null) ExternalEntitySource(externalSource) else IdeUiEntitySource
    )

    if (type != null) {
      diff.addLibraryPropertiesEntity(
        library = libraryEntity,
        libraryType = type.kindId,
        propertiesXmlTag = null,
        source = libraryEntity.entitySource
      )
    }

    return LegacyBridgeLibraryImpl(
      libraryTable = libraryTable,
      project = project,
      initialId = LibraryId(name, libraryTableId),
      initialEntityStore = entityStoreOnDiff,
      initialModifiableModelFactory = this::getLibraryModifiableModel,
      parent = libraryTable
    ).also { myLibrariesToAdd.add(it) }
  }

  override fun removeLibrary(library: Library) {
    assertModelIsLive()

    val currentStorage = entityStoreOnDiff.current

    val entityId = when (library) {
      is LegacyBridgeLibraryImpl -> library.entityId
      is LibraryViaTypedEntity -> library.libraryEntity.persistentId()
      else -> error("Unknown libraryImpl class: ${library.javaClass.simpleName}")
    }

    val libraryEntity = currentStorage.resolve(entityId)
    if (libraryEntity != null) {
      diff.removeEntity(libraryEntity)
      myLibrariesToRemove.add(library)
    }
  }

  override fun commit() {
    assertModelIsLive()
    modelIsCommittedOrDisposed = true

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
