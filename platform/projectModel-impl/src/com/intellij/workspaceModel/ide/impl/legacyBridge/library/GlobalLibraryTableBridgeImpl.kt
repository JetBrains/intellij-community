// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablePresentation
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.platform.workspaceModel.jps.serialization.impl.LibraryNameGenerator
import com.intellij.projectModel.ProjectModelBundle
import com.intellij.util.EventDispatcher
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId

class GlobalLibraryTableBridgeImpl : GlobalLibraryTableBridge, Disposable {
  private val dispatcher = EventDispatcher.create(LibraryTable.Listener::class.java)

  override fun initializeLibraryBridges(changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    val entityStorage = GlobalWorkspaceModel.getInstance().entityStorage
    @Suppress("UNCHECKED_CAST")
    val libraryChanges = (changes[LibraryEntity::class.java] as? List<EntityChange<LibraryEntity>>) ?: emptyList()
    val addChanges = libraryChanges.filterGlobalLibraryChanges().filterIsInstance<EntityChange.Added<LibraryEntity>>()

    for (addChange in addChanges) {
      // Will initialize the bridge if missing
      builder.mutableLibraryMap.getOrPutDataByEntity(addChange.entity) {
        LibraryBridgeImpl(
          libraryTable = this@GlobalLibraryTableBridgeImpl,
          project = null,
          initialId = addChange.entity.symbolicId,
          initialEntityStorage = entityStorage,
          targetBuilder = builder
        )
      }
    }
  }

  override fun initializeLibraryBridgesAfterLoading(mutableStorage: MutableEntityStorage, initialEntityStorage: VersionedEntityStorage): () -> Unit {
    val libraries = mutableStorage
      .entities(LibraryEntity::class.java)
      .filter { it.tableId is LibraryTableId.GlobalLibraryTableId }
      .filter { mutableStorage.libraryMap.getDataByEntity(it) == null }
      .map { libraryEntity ->
        Pair(libraryEntity, LibraryBridgeImpl(
          libraryTable = this@GlobalLibraryTableBridgeImpl,
          project = null,
          initialId = libraryEntity.symbolicId,
          initialEntityStorage = initialEntityStorage,
          targetBuilder = null
        ))
      }
      .toList()
    LOG.debug("Initial load of application-level libraries")
    if (libraries.isEmpty()) {
      return {}
    }

    for ((entity, library) in libraries) {
      mutableStorage.mutableLibraryMap.addIfAbsent(entity, library)
    }

    return {
      // TODO:: Check should we fire this event
      val application = ApplicationManager.getApplication()
      if (application.isWriteAccessAllowed) {
        for ((_, library) in libraries) {
          dispatcher.multicaster.afterLibraryAdded(library)
        }
      } else {
        application.invokeLater {
          runWriteAction {
            for ((_, library) in libraries) {
              dispatcher.multicaster.afterLibraryAdded(library)
            }
          }
        }
      }
    }
  }

  override fun handleBeforeChangeEvents(event: VersionedStorageChange) {
    val removeChanges = event.getChanges(LibraryEntity::class.java).filterGlobalLibraryChanges()
                                                                   .filterIsInstance<EntityChange.Removed<LibraryEntity>>()
    if (removeChanges.isEmpty()) return

    for (change in removeChanges) {
      val library = event.storageBefore.libraryMap.getDataByEntity(change.entity)
      LOG.debug { "Fire 'beforeLibraryRemoved' event for ${change.entity.name}, library = $library" }
      if (library != null) {
        dispatcher.multicaster.beforeLibraryRemoved(library)
      }
    }
  }

  override fun handleChangedEvents(event: VersionedStorageChange) {
    val changes = event.getChanges(LibraryEntity::class.java).filterGlobalLibraryChanges()
    if (changes.isEmpty()) return

    val entityStorage = GlobalWorkspaceModel.getInstance().entityStorage
    for (change in changes) {
      LOG.debug { "Process global library change $change" }
      when (change) {
        is EntityChange.Added -> {
          val alreadyCreatedLibrary = event.storageAfter.libraryMap.getDataByEntity(change.entity) as? LibraryBridgeImpl
                                      ?: error("Library bridge should be created in `before` method")
          alreadyCreatedLibrary.entityStorage = entityStorage
          alreadyCreatedLibrary.clearTargetBuilder()

          dispatcher.multicaster.afterLibraryAdded(alreadyCreatedLibrary)
        }
        is EntityChange.Removed -> {
          val library = event.storageBefore.libraryMap.getDataByEntity(change.entity)

          if (library != null) {
            // TODO There won't be any content in libraryImpl as EntityStore's current was already changed
            dispatcher.multicaster.afterLibraryRemoved(library)
            Disposer.dispose(library)
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

  override fun getLibraries(): Array<Library> {
    val entityStorage = GlobalWorkspaceModel.getInstance().entityStorage
    val storage = entityStorage.current
    val libraryEntitySequence = storage.entities(
      LibraryEntity::class.java).filter { it.tableId::class == LibraryTableId.GlobalLibraryTableId::class }.toList()
    return libraryEntitySequence
      .mapNotNull { storage.libraryMap.getDataByEntity(it) }
      .toList().toTypedArray()
  }

  override fun createLibrary(): Library {
    return createLibrary(null)
  }

  override fun createLibrary(name: String?): Library {
    if (name == null) error("Creating unnamed global libraries is unsupported")

    if (getLibraryByName(name) != null) {
      error("Application library named $name already exists")
    }

    val modifiableModel = modifiableModel
    modifiableModel.createLibrary(name)
    modifiableModel.commit()

    val newLibrary = getLibraryByName(name)
    if (newLibrary == null) {
      error("Library $name was not created")
    }

    return newLibrary
  }

  override fun removeLibrary(library: Library) {
    val modifiableModel = modifiableModel
    modifiableModel.removeLibrary(library)
    modifiableModel.commit()
  }

  override fun getLibraryIterator(): Iterator<Library>  {
    return libraries.iterator()
  }

  override fun getLibraryByName(name: String): Library? {
    val entityStorage = GlobalWorkspaceModel.getInstance().entityStorage
    val libraryId = LibraryId(name, LibraryTableId.GlobalLibraryTableId(LibraryTablesRegistrar.APPLICATION_LEVEL))
    return entityStorage.current.resolve(libraryId)?.let { entity ->
      entityStorage.current.libraryMap.getDataByEntity(entity)
    }
  }

  override fun getTableLevel(): String = LibraryTablesRegistrar.APPLICATION_LEVEL
  override fun getPresentation(): LibraryTablePresentation = GLOBAL_LIBRARY_TABLE_PRESENTATION
  override fun getModifiableModel(): LibraryTable.ModifiableModel = GlobalModifiableLibraryTableBridgeImpl(this)

  override fun dispose() {
    for (library in libraries) {
      Disposer.dispose(library)
    }
  }

  override fun addListener(listener: LibraryTable.Listener) = dispatcher.addListener(listener)
  override fun addListener(listener: LibraryTable.Listener, parentDisposable: Disposable) =
    dispatcher.addListener(listener, parentDisposable)

  override fun removeListener(listener: LibraryTable.Listener) = dispatcher.removeListener(listener)

  companion object {
    private fun List<EntityChange<LibraryEntity>>.filterGlobalLibraryChanges() =
      filter {
        when (it) {
          is EntityChange.Added -> it.entity.tableId is LibraryTableId.GlobalLibraryTableId
          is EntityChange.Removed -> it.entity.tableId is LibraryTableId.GlobalLibraryTableId
          is EntityChange.Replaced -> it.oldEntity.tableId is LibraryTableId.GlobalLibraryTableId
        }
      }

    private val GLOBAL_LIBRARY_TABLE_PRESENTATION: LibraryTablePresentation = object : LibraryTablePresentation() {
      override fun getDisplayName(plural: Boolean): String {
        return ProjectModelBundle.message("global.library.display.name", if (plural) 2 else 1)
      }

      override fun getDescription(): String {
        return ProjectModelBundle.message("libraries.node.text.ide")
      }

      override fun getLibraryTableEditorTitle(): String {
        return ProjectModelBundle.message("library.configure.global.title")
      }
    }
    private val LOG = logger<GlobalLibraryTableBridgeImpl>()
  }
}