// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.google.common.collect.HashBiMap
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.impl.ModuleLibraryTableBase
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.internal
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.serialization.impl.LibraryNameGenerator
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeModifiableBase
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.findLibraryEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

internal class ModifiableModuleLibraryTableBridge(private val modifiableModel: ModifiableRootModelBridgeImpl)
  : ModuleLibraryTableBase(), ModuleLibraryTableBridge {
  private val copyToOriginal = HashBiMap.create<LibraryBridge, LibraryBridge>()

  init {
    val storage = modifiableModel.moduleBridge.entityStorage.current
    libraryEntities()
      .forEach { libraryEntry ->
        val originalLibrary = storage.libraryMap.getDataByEntity(libraryEntry)
        if (originalLibrary != null) {
          //if a module-level library from ModifiableRootModel is changed, the changes must not be committed to the model until
          //ModifiableRootModel is committed. So we place copies of LibraryBridge instances to the modifiable model. If the model is disposed
          //these copies are disposed; if the model is committed only changed copies will be included to the model, otherwise they will be disposed.
          val modifiableCopy = LibraryBridgeImpl(this, modifiableModel.project, libraryEntry.symbolicId,
                                                 modifiableModel.entityStorageOnDiff,
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

    val libraryEntityName = LibraryNameGenerator.generateLibraryEntityName(name) { existsName ->
      LibraryId(existsName, tableId) in modifiableModel.diff
    }

    val libraryEntity = modifiableModel.diff addEntity LibraryEntity(name = libraryEntityName,
                                                                     tableId = tableId,
                                                                     roots = emptyList(),
                                                                     entitySource = modifiableModel.moduleEntity.entitySource)

    if (type != null) {
      modifiableModel.diff addEntity LibraryPropertiesEntity(libraryType = type.kindId,
                                                             entitySource = libraryEntity.entitySource) {
        library = libraryEntity
        propertiesXmlTag = LegacyBridgeModifiableBase.serializeComponentAsString(JpsLibraryTableSerializer.PROPERTIES_TAG,
                                                                                 type.createDefaultProperties())
      }
    }

    return createAndAddLibrary(libraryEntity, false, ModuleDependencyItem.DependencyScope.COMPILE)
  }

  private fun createAndAddLibrary(libraryEntity: LibraryEntity, exported: Boolean,
                                  scope: ModuleDependencyItem.DependencyScope): LibraryBridgeImpl {
    val libraryId = libraryEntity.symbolicId

    modifiableModel.appendDependency(ModuleDependencyItem.Exportable.LibraryDependency(library = libraryId, exported = exported,
                                                                                       scope = scope))

    val library = LibraryBridgeImpl(
      libraryTable = ModuleRootComponentBridge.getInstance(modifiableModel.module).moduleLibraryTable,
      project = modifiableModel.project,
      initialId = libraryEntity.symbolicId,
      initialEntityStorage = modifiableModel.entityStorageOnDiff,
      targetBuilder = modifiableModel.diff
    )
    modifiableModel.diff.mutableLibraryMap.addMapping(libraryEntity, library)
    return library
  }

  private fun getTableId() = LibraryTableId.ModuleLibraryTableId(modifiableModel.moduleEntity.symbolicId)

  internal fun addLibraryCopy(original: LibraryBridgeImpl,
                              exported: Boolean,
                              scope: ModuleDependencyItem.DependencyScope): LibraryBridgeImpl {
    val tableId = getTableId()
    val libraryEntityName = LibraryNameGenerator.generateLibraryEntityName(original.name) { existsName ->
      LibraryId(existsName, tableId) in modifiableModel.diff
    }
    val originalEntity = original.librarySnapshot.libraryEntity
    val libraryEntity = modifiableModel.diff addEntity LibraryEntity(name = libraryEntityName,
                                                                     tableId = tableId,
                                                                     roots = originalEntity.roots,
                                                                     entitySource = modifiableModel.moduleEntity.entitySource
    ) {
      excludedRoots = originalEntity.excludedRoots
    }

    val originalProperties = originalEntity.libraryProperties
    if (originalProperties != null) {
      modifiableModel.diff addEntity LibraryPropertiesEntity(libraryType = originalProperties.libraryType,
                                                             entitySource = libraryEntity.entitySource) {
        library = libraryEntity
        propertiesXmlTag = originalProperties.propertiesXmlTag
      }
    }
    return createAndAddLibrary(libraryEntity, exported, scope)
  }

  override fun removeLibrary(library: Library) {
    modifiableModel.assertModelIsLive()
    library as LibraryBridge

    var copyBridgeForDispose: LibraryBridge? = null
    val libraryEntity = modifiableModel.diff.findLibraryEntity(library) ?: run {
      copyToOriginal.inverse()[library]?.let { libraryCopy ->
        copyBridgeForDispose = libraryCopy
        modifiableModel.diff.findLibraryEntity(libraryCopy)
      }
    }

    if (libraryEntity == null) {
      LOG.error("Cannot find entity for library ${library.name}")
      return
    }

    val libraryId = libraryEntity.symbolicId
    modifiableModel.removeDependencies { _, item ->
      item is ModuleDependencyItem.Exportable.LibraryDependency && item.library == libraryId
    }

    modifiableModel.diff.removeEntity(libraryEntity)
    Disposer.dispose(library)
    if (copyBridgeForDispose != null) {
      Disposer.dispose(copyBridgeForDispose!!)
    }
  }

  internal fun restoreLibraryMappingsAndDisposeCopies() {
    libraryIterator.forEach {
      val originalLibrary = copyToOriginal[it]
      //originalLibrary may be null if the library was added after the table was created
      if (originalLibrary != null) {
        val mutableLibraryMap = modifiableModel.diff.mutableLibraryMap
        mutableLibraryMap.addMapping(mutableLibraryMap.getEntities(it as LibraryBridge).single(), originalLibrary)
      }

      Disposer.dispose(it)
    }
  }

  internal fun restoreMappingsForUnchangedLibraries(changedLibs: Set<LibraryId>) {
    if (copyToOriginal.isEmpty()) return

    copyToOriginal.forEach { (copyBridge, originBridge) ->
      // If library was removed its instance of bridge already be disposed
      if (copyBridge.isDisposed || originBridge.isDisposed) return@forEach
      if (!changedLibs.contains(originBridge.libraryId) && originBridge.hasSameContent(copyBridge)) {
        val mutableLibraryMap = modifiableModel.diff.mutableLibraryMap
        mutableLibraryMap.addMapping(mutableLibraryMap.getEntities(copyBridge as LibraryBridge).single(), originBridge)
        Disposer.dispose(copyBridge)
      }
    }
  }

  /**
   * We should iterate through all created bridges' copies and original one which are actual for this moment and
   * update storage and libraryTable for them.
   * For the newly created libs this will be done in [ModuleManagerComponentBridge#processModuleLibraryChange]
   */
  internal fun disposeOriginalLibrariesAndUpdateCopies() {
    if (copyToOriginal.isEmpty()) return

    val storage = WorkspaceModel.getInstance(modifiableModel.project).internal.entityStorage
    copyToOriginal.forEach { (copyBridge, originBridge) ->
      // It's possible if we removed old library, its copy will be disposed [ModifiableModuleLibraryTableBridge.removeLibrary]
      // but original bridge will be disposed in during events handling. This method will be called the last thus both of them will be disposed
      if (copyBridge.isDisposed && originBridge.isDisposed) return@forEach

      val (actualBridge, outdatedBridge) = if (storage.current.libraryMap.getFirstEntity(copyBridge) != null) {
        copyBridge to originBridge
      } else if (storage.current.libraryMap.getFirstEntity(originBridge) != null) {
        originBridge to copyBridge
      } else {
        error("Unexpected state that both bridges are not actual")
      }
      actualBridge as LibraryBridgeImpl
      actualBridge.entityStorage = storage
      actualBridge.libraryTable = ModuleRootComponentBridge.getInstance(module).moduleLibraryTable
      actualBridge.clearTargetBuilder()
      Disposer.dispose(outdatedBridge)
    }
  }

  override fun isChanged(): Boolean {
    return modifiableModel.isChanged
  }

  override val module: Module
    get() = modifiableModel.module

  companion object {
    private val LOG = logger<ModifiableModuleLibraryTableBridge>()
  }
}