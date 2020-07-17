// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.impl.ModuleLibraryTableBase
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Disposer
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder

internal class ModuleLibraryTableBridgeImpl(private val moduleBridge: ModuleBridge) : ModuleLibraryTableBase(), ModuleLibraryTableBridge {
  init {
    Disposer.register(moduleBridge, this)
  }

  internal fun registerModuleLibraryInstances(builder: WorkspaceEntityStorageDiffBuilder?) {
    libraryEntities().forEach { addLibrary(it, builder) }
  }

  private fun libraryEntities(): Sequence<LibraryEntity> {
    val moduleLibraryTableId = LibraryTableId.ModuleLibraryTableId(moduleBridge.moduleEntityId)

    return moduleBridge.entityStorage.current
      .entities(LibraryEntity::class.java)
      .filter { it.tableId == moduleLibraryTableId }
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

  internal fun addLibrary(entity: LibraryEntity, storageBuilder: WorkspaceEntityStorageDiffBuilder?): LibraryBridgeImpl {
    val library = LibraryBridgeImpl(
      libraryTable = this,
      project = module.project,
      initialId = entity.persistentId(), 
      initialEntityStorage = moduleBridge.entityStorage,
      targetBuilder = null
    )
    if (storageBuilder != null) {
      storageBuilder.mutableLibraryMap.addMapping(entity, library)
    }
    else {
      runWriteAction {
        WorkspaceModel.getInstance(moduleBridge.project).updateProjectModelSilent {
          it.mutableLibraryMap.addMapping(entity, library)
        }
      }
    }
    return library
  }

  override fun dispose() {
    for (library in libraryIterator) {
      if (!Disposer.isDisposed(library)) Disposer.dispose(library)
    }
  }

  override val module: Module
    get() = moduleBridge
}
