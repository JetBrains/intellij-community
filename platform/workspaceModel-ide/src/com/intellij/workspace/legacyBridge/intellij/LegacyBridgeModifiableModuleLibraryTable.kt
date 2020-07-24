// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.impl.ModuleLibraryTableBase
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.workspace.api.*
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibrary
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibraryImpl
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeModifiableBase
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

internal class LegacyBridgeModifiableModuleLibraryTable(private val modifiableModel: LegacyBridgeModifiableRootModel,
                                                        originalLibraries: List<LegacyBridgeLibraryImpl>) : ModuleLibraryTableBase(), LegacyBridgeModuleLibraryTable {
  private val libraries: MutableList<LegacyBridgeLibraryImpl> = originalLibraries.mapTo(ArrayList()) {
    LegacyBridgeLibraryImpl(this, modifiableModel.project, it.libraryId, modifiableModel.entityStoreOnDiff, this, modifiableModel.diff)
  }

  override fun getLibraryIterator(): Iterator<Library> {
    return libraries.iterator()
  }

  // TODO Support externalSource. Could it be different from module's?
  override fun createLibrary(name: String?,
                             type: PersistentLibraryKind<*>?,
                             externalSource: ProjectModelExternalSource?): Library {

    modifiableModel.assertModelIsLive()

    val tableId = getTableId()

    val libraryEntityName = LegacyBridgeLibraryImpl.generateLibraryEntityName(name) { existsName ->
      modifiableModel.diff.resolve(LibraryId(existsName, tableId)) != null
    }

    val libraryEntity = modifiableModel.diff.addLibraryEntity(
      roots = emptyList(),
      tableId = tableId,
      name = libraryEntityName,
      excludedRoots = emptyList(),
      source = modifiableModel.moduleEntity.entitySource
    )

    if (type != null) {
      modifiableModel.diff.addLibraryPropertiesEntity(
        library = libraryEntity,
        libraryType = type.kindId,
        propertiesXmlTag = LegacyBridgeModifiableBase.serializeComponentAsString(JpsLibraryTableSerializer.PROPERTIES_TAG, type.createDefaultProperties()),
        source = libraryEntity.entitySource
      )
    }

    val libraryId = libraryEntity.persistentId()

    modifiableModel.updateDependencies {
      it + ModuleDependencyItem.Exportable.LibraryDependency(
        library = libraryId,
        exported = false,
        scope = ModuleDependencyItem.DependencyScope.COMPILE
      )
    }

    val libraryImpl = createLibrary(libraryId)
    libraries.add(libraryImpl)
    return libraryImpl
  }

  private fun createLibrary(libraryId: LibraryId): LegacyBridgeLibraryImpl {

    return LegacyBridgeLibraryImpl(
      libraryTable = LegacyBridgeModuleRootComponent.getInstance(modifiableModel.module).moduleLibraryTable,
      project = modifiableModel.project,
      initialId = libraryId,
      initialEntityStore = modifiableModel.entityStoreOnDiff,
      parent = this,
      targetBuilder = modifiableModel.diff
    )
  }

  private fun getTableId() = LibraryTableId.ModuleLibraryTableId(modifiableModel.moduleEntity.persistentId())

  fun createLibraryCopy(library: LegacyBridgeLibraryImpl): LegacyBridgeLibraryImpl {
    val tableId = getTableId()
    val libraryEntityName = LegacyBridgeLibraryImpl.generateLibraryEntityName(library.name) { existsName ->
      modifiableModel.diff.resolve(LibraryId(existsName, tableId)) != null
    }
    val originalEntity = library.librarySnapshot.libraryEntity
    val libraryEntity = modifiableModel.diff.addLibraryEntity(
      roots = originalEntity.roots,
      tableId = tableId,
      name = libraryEntityName,
      excludedRoots = originalEntity.excludedRoots,
      source = modifiableModel.moduleEntity.entitySource
    )

    val originalProperties = originalEntity.getCustomProperties()
    if (originalProperties != null) {
      modifiableModel.diff.addLibraryPropertiesEntity(
        library = libraryEntity,
        libraryType = originalProperties.libraryType,
        propertiesXmlTag = originalProperties.propertiesXmlTag,
        source = libraryEntity.entitySource
      )
    }
    return createLibrary(libraryEntity.persistentId())
  }

  fun addCopiedLibrary(library: LegacyBridgeLibraryImpl) {
    libraries.add(library)
  }

  override fun removeLibrary(library: Library) {
    modifiableModel.assertModelIsLive()
    library as LegacyBridgeLibrary

    val moduleLibraryTableId = library.libraryId.tableId as LibraryTableId.ModuleLibraryTableId
    if (moduleLibraryTableId.moduleId != modifiableModel.moduleId) {
      error("removeLibrary should be called on a module library from the same module. " +
            "Library moduleId: '${moduleLibraryTableId.moduleId}', this model moduleId: '${modifiableModel.moduleId}'")
    }

    val libraryEntity = library.libraryId.resolve(modifiableModel.entityStoreOnDiff.current)
                        ?: error("Unable to resolve module library by id: ${library.libraryId}")

    modifiableModel.updateDependencies { dependencies ->
      dependencies.filterNot { it is ModuleDependencyItem.Exportable.LibraryDependency && it.library == library.libraryId }
    }

    modifiableModel.diff.removeEntity(libraryEntity)
    libraries.remove(library)
  }

  override fun isChanged(): Boolean {
    return modifiableModel.isChanged
  }

  override val module: Module
    get() = modifiableModel.module
}