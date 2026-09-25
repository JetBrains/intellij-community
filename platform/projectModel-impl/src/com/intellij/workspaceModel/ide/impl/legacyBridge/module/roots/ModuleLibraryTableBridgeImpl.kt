// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.impl.ModuleLibraryTableBase
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Disposer
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryOrigin
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.moduleMap
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import org.jetbrains.annotations.ApiStatus

/**
 * This class methods [registerModuleLibraryInstances], [takeOverModuleLibraryInstances], [addLibrary] should be marked as internal
 * after [ModuleManagerComponentBridge] migration to the `intellij.platform.projectModel.impl` module
 */
@ApiStatus.Internal
class ModuleLibraryTableBridgeImpl(private val moduleBridge: ModuleBridge) : ModuleLibraryTableBase(), ModuleLibraryTableBridge {
  init {
    Disposer.register(moduleBridge, this)
  }

  fun registerModuleLibraryInstances(builder: MutableEntityStorage) {
    libraryEntities().forEach { addLibrary(it, builder) }
  }

  /**
   * Makes this table the owner of the live library bridges that [builder] maps for the module entity.
   * The module entity can go away and come back while its library entities stay.
   * The new module bridge then takes over the library bridges from the table of the old module bridge.
   */
  fun takeOverModuleLibraryInstances(builder: MutableEntityStorage) {
    val libraryMap = builder.libraryMap
    for (entity in builder.referrers(moduleBridge.moduleEntityId, LibraryEntity::class.java)) {
      val library = libraryMap.getDataByEntity(entity) as? LibraryBridgeImpl ?: continue
      if (library.libraryTable === this || library.isDisposed) continue
      library.libraryTable = this
      library.cleanCachedValue()
    }
  }

  private fun libraryEntities(): Sequence<LibraryEntity> {
    return moduleBridge.entityStorage.current.referrers(moduleBridge.moduleEntityId, LibraryEntity::class.java)
  }

  override fun getLibraryIterator(): Iterator<Library> {
    val storage = moduleBridge.entityStorage.current
    return libraryEntities().mapNotNull { storage.libraryMap.getDataByEntity(it) }.iterator()
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

  fun addLibrary(entity: LibraryEntity, storageBuilder: MutableEntityStorage): LibraryBridgeImpl {
    val library = LibraryBridgeImpl(
      libraryTable = this,
      origin = LibraryOrigin.OfProject(module.project),
      initialId = entity.symbolicId,
      initialEntityStorage = moduleBridge.entityStorage,
      targetBuilder = storageBuilder
    )
    storageBuilder.mutableLibraryMap.addMapping(entity, library)
    return library
  }

  /**
   * Disposes the library bridges that this table owns.
   * A library bridge stays alive when its storage still maps it after the module bridge leaves that storage.
   * The listener then disposes it together with its library entity.
   */
  override fun dispose() {
    for (library in libraryIterator) {
      library as LibraryBridgeImpl
      if (library.libraryTable !== this || library.isDisposed || library.outlivesModuleBridge()) continue
      LibraryBridgeImpl.disposeLibrary(library)
    }
  }

  private fun LibraryBridgeImpl.outlivesModuleBridge(): Boolean {
    val storage = entityStorage.current
    if (storage.libraryMap.getFirstEntity(this) == null) return false
    val moduleEntity = storage.resolve(moduleBridge.moduleEntityId) ?: return true
    return storage.moduleMap.getDataByEntity(moduleEntity) !== moduleBridge
  }

  override val module: Module
    get() = moduleBridge
}
