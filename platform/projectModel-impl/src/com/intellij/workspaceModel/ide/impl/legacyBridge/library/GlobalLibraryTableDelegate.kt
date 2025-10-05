// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.serialization.impl.LibraryNameGenerator
import com.intellij.platform.workspace.storage.*
import com.intellij.util.EventDispatcher
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap

internal class GlobalLibraryTableDelegate(private val libraryTable: LibraryTable, val eelMachine: EelMachine, private val libraryTableId: LibraryTableId) : Disposable {
  private val dispatcher = EventDispatcher.create(LibraryTable.Listener::class.java)

  internal fun initializeLibraryBridges(changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    val entityStorage = GlobalWorkspaceModel.getInstance(eelMachine).entityStorage

    @Suppress("UNCHECKED_CAST")
    val libraryChanges = (changes[LibraryEntity::class.java] as? List<EntityChange<LibraryEntity>>) ?: emptyList()
    val addChanges = libraryChanges.filterLibraryChanges(libraryTableId).filterIsInstance<EntityChange.Added<LibraryEntity>>()

    for (addChange in addChanges) {
      // Will initialize the bridge if missing
      builder.mutableLibraryMap.getOrPutDataByEntity(addChange.newEntity) {
        LibraryBridgeImpl(
          libraryTable = libraryTable,
          origin = LibraryOrigin.OfMachine(eelMachine),
          initialId = addChange.newEntity.symbolicId,
          initialEntityStorage = entityStorage,
          targetBuilder = builder
        )
      }
    }
  }

  internal fun initializeLibraryBridgesAfterLoading(mutableStorage: MutableEntityStorage,
                                                    initialEntityStorage: VersionedEntityStorage): () -> Unit {
    val libraries = mutableStorage
      .entities(LibraryEntity::class.java)
      .filter { it.tableId == libraryTableId }
      .filter { mutableStorage.libraryMap.getDataByEntity(it) == null }
      .map { libraryEntity ->
        Pair(libraryEntity, LibraryBridgeImpl(
          libraryTable = libraryTable,
          origin = LibraryOrigin.OfMachine(eelMachine),
          initialId = libraryEntity.symbolicId,
          initialEntityStorage = initialEntityStorage,
          targetBuilder = null
        ))
      }
      .toList()
    LOG.debug("Initial load of ${libraryTableId.level}-level libraries")
    if (libraries.isEmpty()) {
      return {}
    }

    for ((entity, library) in libraries) {
      mutableStorage.mutableLibraryMap.addIfAbsent(entity, library)
    }

    val action: () -> Unit = {
      // TODO:: Check should we fire this event
      val application = ApplicationManager.getApplication()
      if (application.isWriteAccessAllowed) {
        for ((_, library) in libraries) {
          dispatcher.multicaster.afterLibraryAdded(library)
        }
      }
      else {
        application.invokeLater {
          runWriteAction {
            for ((_, library) in libraries) {
              dispatcher.multicaster.afterLibraryAdded(library)
            }
          }
        }
      }
    }
    return action
  }

  internal fun handleBeforeChangeEvents(event: VersionedStorageChange) {
    val removeChanges = event.getChanges(LibraryEntity::class.java).filterLibraryChanges(libraryTableId)
      .filterIsInstance<EntityChange.Removed<LibraryEntity>>()
    if (removeChanges.isEmpty()) return

    for (change in removeChanges) {
      val library = event.storageBefore.libraryMap.getDataByEntity(change.oldEntity)
      //LOG.debug { "Fire 'beforeLibraryRemoved' event for ${change.entity.name}, library = $library" }
      if (library != null) {
        dispatcher.multicaster.beforeLibraryRemoved(library)
      }
    }
  }

  internal fun handleChangedEvents(event: VersionedStorageChange) {
    val changes = event.getChanges(LibraryEntity::class.java).filterLibraryChanges(libraryTableId)
    if (changes.isEmpty()) return

    val entityStorage = GlobalWorkspaceModel.getInstance(eelMachine).entityStorage
    for (change in changes) {
      LOG.debug { "Process ${libraryTableId.level} library change $change" }
      when (change) {
        is EntityChange.Added -> {
          val alreadyCreatedLibrary = event.storageAfter.libraryMap.getDataByEntity(change.newEntity) as? LibraryBridgeImpl
                                      ?: error("Library bridge should be created in `before` method")
          alreadyCreatedLibrary.entityStorage = entityStorage
          alreadyCreatedLibrary.clearTargetBuilder()

          dispatcher.multicaster.afterLibraryAdded(alreadyCreatedLibrary)
        }
        is EntityChange.Removed -> {
          val library = event.storageBefore.libraryMap.getDataByEntity(change.oldEntity)

          if (library != null) {
            // TODO There won't be any content in libraryImpl as EntityStore's current was already changed
            dispatcher.multicaster.afterLibraryRemoved(library)
            LibraryBridgeImpl.disposeLibrary(library)
          }
        }
        is EntityChange.Replaced -> {
          val idBefore = change.oldEntity.symbolicId
          val idAfter = change.newEntity.symbolicId

          if (idBefore != idAfter) {
            val library = event.storageBefore.libraryMap.getDataByEntity(change.oldEntity) as? LibraryBridgeImpl
            if (library != null) {
              library.entityId = idAfter
              dispatcher.multicaster.afterLibraryRenamed(library, LibraryNameGenerator.getLegacyLibraryName(idBefore))
            }
          }
        }
      }
    }
  }

  fun fireRootSetChanged(libraryEntity: LibraryEntity, entityStorage: EntityStorage) {
    (entityStorage.libraryMap.getDataByEntity(libraryEntity) as? LibraryBridgeImpl)?.fireRootSetChanged()
  }

  internal fun createLibrary(name: String?): Library {
    if (name == null) error("Creating unnamed ${libraryTableId.level} libraries is unsupported")

    if (getLibraryByName(name) != null) {
      error("${libraryTableId.level} library named $name already exists")
    }

    val modifiableModel = libraryTable.modifiableModel
    modifiableModel.createLibrary(name)
    modifiableModel.commit()

    val newLibrary = getLibraryByName(name)
    if (newLibrary == null) {
      error("Library $name was not created")
    }

    return newLibrary
  }

  internal fun removeLibrary(library: Library) {
    val modifiableModel = libraryTable.modifiableModel
    modifiableModel.removeLibrary(library)
    modifiableModel.commit()
  }

  internal fun getLibraries(): Array<Library> {
    val entityStorage = GlobalWorkspaceModel.getInstance(eelMachine).entityStorage
    val storage = entityStorage.current
    val libraryEntitySequence = storage.entities(LibraryEntity::class.java).filter { it.tableId == libraryTableId }.toList()
    val libs: Array<Library> = libraryEntitySequence.mapNotNull { storage.libraryMap.getDataByEntity(it) }
      .toList().toTypedArray()
    return libs
  }

  internal fun getLibraryByName(name: String): Library? {
    val entityStorage = GlobalWorkspaceModel.getInstance(eelMachine).entityStorage
    val libraryId = LibraryId(name, libraryTableId)
    val library = entityStorage.current.resolve(libraryId)?.let { entity ->
      entityStorage.current.libraryMap.getDataByEntity(entity)
    }
    return library
  }

  override fun dispose() {
    for (library in getLibraries()) {
      LibraryBridgeImpl.disposeLibrary(library)
    }
  }

  internal fun addListener(listener: LibraryTable.Listener) = dispatcher.addListener(listener)

  internal fun addListener(listener: LibraryTable.Listener, parentDisposable: Disposable) {
    dispatcher.addListener(listener, parentDisposable)
  }

  internal fun removeListener(listener: LibraryTable.Listener) = dispatcher.removeListener(listener)

  companion object {
    private val LOG = logger<GlobalLibraryTableDelegate>()
  }
}

private fun List<EntityChange<LibraryEntity>>.filterLibraryChanges(libraryTableId: LibraryTableId): List<EntityChange<LibraryEntity>> {
  return filter {
    val tableId = when (it) {
      is EntityChange.Added -> it.newEntity.tableId
      is EntityChange.Removed -> it.oldEntity.tableId
      is EntityChange.Replaced -> it.oldEntity.tableId
    }
    tableId == libraryTableId
  }
}