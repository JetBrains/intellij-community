// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.impl.ModuleLibraryTableBase
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Disposer
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeModifiableBase
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.findLibraryEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap
import com.intellij.workspaceModel.storage.bridgeEntities.*
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

internal class ModifiableModuleLibraryTableBridge(private val modifiableModel: ModifiableRootModelBridge)
  : ModuleLibraryTableBase(), ModuleLibraryTableBridge {
  private val copyToOriginal = HashMap<LibraryBridge, LibraryBridge>()
  init {
    val storage = modifiableModel.moduleBridge.entityStorage.current
    libraryEntities()
      .forEach { libraryEntry ->
        val originalLibrary = storage.libraryMap.getDataByEntity(libraryEntry)
        if (originalLibrary != null) {
          //if a module-level library from ModifiableRootModel is changed, the changes must not be committed to the model until
          //ModifiableRootModel is committed. So we place copies of LibraryBridge instances to the modifiable model. If the model is disposed
          //these copies are disposed; if the model is committed they'll be included to the model, and the original instances will be disposed.
          val modifiableCopy = LibraryBridgeImpl(this, modifiableModel.project, libraryEntry.persistentId(), modifiableModel.entityStorageOnDiff,
                                                 modifiableModel.diff)
          copyToOriginal[modifiableCopy] = originalLibrary
          modifiableModel.diff.mutableLibraryMap.addMapping(libraryEntry, modifiableCopy)
        }
      }
  }

  private fun libraryEntities(): Sequence<LibraryEntity> {
    val moduleLibraryTableId = getTableId()
    return modifiableModel.entityStorageOnDiff.current
      .entities(LibraryEntity::class.java)
      .filter { it.tableId == moduleLibraryTableId }
  }

  override fun getLibraryIterator(): Iterator<Library> {
    val storage = modifiableModel.entityStorageOnDiff.current
    return libraryEntities().mapNotNull { storage.libraryMap.getDataByEntity(it) }.iterator()
  }

  override fun createLibrary(name: String?,
                             type: PersistentLibraryKind<*>?,
                             externalSource: ProjectModelExternalSource?): Library {

    modifiableModel.assertModelIsLive()

    val tableId = getTableId()

    val libraryEntityName = LibraryBridgeImpl.generateLibraryEntityName(name) { existsName ->
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

    return createAndAddLibrary(libraryEntity, false, ModuleDependencyItem.DependencyScope.COMPILE)
  }

  private fun createAndAddLibrary(libraryEntity: LibraryEntity, exported: Boolean,
                                  scope: ModuleDependencyItem.DependencyScope): LibraryBridgeImpl {
    val libraryId = libraryEntity.persistentId()

    modifiableModel.updateDependencies {
      it + ModuleDependencyItem.Exportable.LibraryDependency(
        library = libraryId,
        exported = exported,
        scope = scope
      )
    }

    val library = LibraryBridgeImpl(
      libraryTable = ModuleRootComponentBridge.getInstance(modifiableModel.module).moduleLibraryTable,
      project = modifiableModel.project,
      initialId = libraryEntity.persistentId(),
      initialEntityStorage = modifiableModel.entityStorageOnDiff,
      targetBuilder = modifiableModel.diff
    )
    modifiableModel.diff.mutableLibraryMap.addMapping(libraryEntity, library)
    return library
  }

  private fun getTableId() = LibraryTableId.ModuleLibraryTableId(modifiableModel.moduleEntity.persistentId())

  internal fun addLibraryCopy(original: LibraryBridgeImpl, exported: Boolean, scope: ModuleDependencyItem.DependencyScope): LibraryBridgeImpl {
    val tableId = getTableId()
    val libraryEntityName = LibraryBridgeImpl.generateLibraryEntityName(original.name) { existsName ->
      modifiableModel.diff.resolve(LibraryId(existsName, tableId)) != null
    }
    val originalEntity = original.librarySnapshot.libraryEntity
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
    return createAndAddLibrary(libraryEntity, exported, scope)
  }

  override fun removeLibrary(library: Library) {
    modifiableModel.assertModelIsLive()
    library as LibraryBridge

    val libraryEntity = modifiableModel.diff.findLibraryEntity(library)
                        ?: error("Cannot find entity for library ${library.name}")

    val libraryId = libraryEntity.persistentId()
    modifiableModel.updateDependencies { dependencies ->
      dependencies.filterNot { it is ModuleDependencyItem.Exportable.LibraryDependency && it.library == libraryId }
    }

    modifiableModel.diff.removeEntity(libraryEntity)
    Disposer.dispose(library)
  }

  internal fun disposeLibraryCopies() {
    libraryIterator.forEach {
      Disposer.dispose(it)
    }
  }

  internal fun disposeOriginalLibraries() {
    libraryIterator.forEach {
      val original = copyToOriginal[it]
      if (original != null) {
        Disposer.dispose(original)
      }
    }
  }

  override fun isChanged(): Boolean {
    return modifiableModel.isChanged
  }

  override val module: Module
    get() = modifiableModel.module
}